// Package sandbox exposes only bounded strings to the Android binding. Scripts
// receive Starlark values, never Go, Java, Android, storage or network objects.
package sandbox

import (
	"encoding/json"
	"fmt"
	"math"
	"math/big"
	"runtime"
	"runtime/debug"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"go.starlark.net/starlark"
	"go.starlark.net/starlarkstruct"
	"go.starlark.net/syntax"
)

const (
	maxCode        = 32768
	maxInput       = 16384
	maxObservation = 131072
	maxState       = 8192
	maxOutput      = 65536
	maxLog         = 8192
	maxActions     = 32
	maxSteps       = 100000
)

// Run evaluates one program and returns a JSON result/operation plan. It does not
// execute device operations. The parent validates the complete plan before use.
func Run(code, input, observation, state string) (string, error) {
	if len(code) == 0 || len(code) > maxCode {
		return "", fmt.Errorf("Starlark source limit exceeded")
	}
	previousLimit := debug.SetMemoryLimit(48 * 1024 * 1024)
	defer debug.SetMemoryLimit(previousLimit)
	previousProcs := runtime.GOMAXPROCS(2)
	defer runtime.GOMAXPROCS(previousProcs)
	thread := &starlark.Thread{Name: "goal"}
	thread.SetMaxExecutionSteps(maxSteps)
	started := time.Now()
	timer := time.AfterFunc(2*time.Second, func() { thread.Cancel("Starlark time limit exceeded") })
	defer timer.Stop()
	args, err := decodeObject(input, maxInput)
	if err != nil {
		return "", err
	}
	screen, err := decodeObject(observation, maxObservation)
	if err != nil {
		return "", err
	}
	store, err := decodeObject(state, maxState)
	if err != nil {
		return "", err
	}
	args.Freeze()
	screen.Freeze()
	api := &invocation{screen: screen, actions: starlark.NewList(nil)}
	thread.Print = func(_ *starlark.Thread, text string) {
		if err := api.appendLog(text); err != nil {
			api.logError = err
			thread.Cancel(err.Error())
		}
	}
	globals, err := starlark.ExecFileOptions(&syntax.FileOptions{}, thread, "goal.star", code, starlark.StringDict{
		"input": args, "screen": screen, "state": store, "ui": api.ui(),
		"log":   starlark.NewBuiltin("log", api.log),
		"macro": starlark.NewBuiltin("macro", api.macro),
	})
	if err != nil {
		return "", err
	}
	main, ok := globals["main"].(starlark.Callable)
	if !ok {
		return "", fmt.Errorf("Define main() and return a JSON-compatible value")
	}
	value, err := starlark.Call(thread, main, nil, nil)
	if err != nil {
		return "", err
	}
	if api.logError != nil {
		return "", api.logError
	}
	if _, err := encode(store, maxState); err != nil {
		return "", fmt.Errorf("state: %w", err)
	}
	output := starlark.NewDict(4)
	for _, item := range []struct {
		key   string
		value starlark.Value
	}{{"result", value}, {"state", store}, {"actions", api.actions}, {"logs", starlark.String(api.logs.String())}} {
		if err := output.SetKey(starlark.String(item.key), item.value); err != nil {
			return "", err
		}
	}
	encoded, err := encode(output, maxOutput)
	if time.Since(started) >= 2*time.Second {
		return "", fmt.Errorf("Starlark time limit exceeded")
	}
	return encoded, err
}

type invocation struct {
	screen   *starlark.Dict
	actions  *starlark.List
	logs     strings.Builder
	logError error
}

type pixel float64

func (value *pixel) Unpack(input starlark.Value) error {
	number, ok := starlark.AsFloat(input)
	if !ok || math.IsNaN(number) || math.IsInf(number, 0) {
		return fmt.Errorf("Expected a finite pixel coordinate")
	}
	*value = pixel(number)
	return nil
}

func (api *invocation) appendLog(text string) error {
	if api.logs.Len()+len(text)+1 > maxLog {
		return fmt.Errorf("Log limit exceeded")
	}
	api.logs.WriteString(text)
	api.logs.WriteByte('\n')
	return nil
}

func (api *invocation) log(_ *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var value starlark.Value
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "value", &value); err != nil {
		return nil, err
	}
	text, err := encode(value, maxLog)
	if err != nil {
		return nil, err
	}
	if s, ok := starlark.AsString(value); ok {
		text = s
	}
	return starlark.None, api.appendLog(text)
}

func (api *invocation) queue(action starlark.Value) error {
	if _, ok := action.(*starlark.Dict); !ok {
		return fmt.Errorf("Macro items must be dictionaries")
	}
	if api.actions.Len() >= maxActions {
		return fmt.Errorf("Macro limit exceeded")
	}
	// Serialize/reparse: no mutable aliases can change an already queued operation.
	text, err := encode(action, 32768)
	if err != nil {
		return err
	}
	copy, err := decodeObject(text, 32768)
	if err != nil {
		return err
	}
	copy.Freeze()
	if err := api.actions.Append(copy); err != nil {
		return err
	}
	_, err = encode(api.actions, 32768)
	return err
}

func (api *invocation) macro(_ *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var batch *starlark.List
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "actions", &batch); err != nil {
		return nil, err
	}
	if batch.Len() == 0 || batch.Len()+api.actions.Len() > maxActions {
		return nil, fmt.Errorf("Macro must contain 1 to 32 actions")
	}
	for i := 0; i < batch.Len(); i++ {
		if err := api.queue(batch.Index(i)); err != nil {
			return nil, err
		}
	}
	return starlark.None, nil
}

func (api *invocation) ui() starlark.Value {
	methods := starlark.StringDict{}
	for _, name := range []string{"tap", "fill", "tap_at", "long_press_at", "swipe", "text", "input_text", "enter", "launch", "back", "home", "recents", "notifications", "quick_settings", "wait"} {
		methods[name] = starlark.NewBuiltin("ui."+name, api.command(name))
	}
	return starlarkstruct.FromStringDict(starlark.String("ui"), methods)
}

func (api *invocation) command(name string) func(*starlark.Thread, *starlark.Builtin, starlark.Tuple, []starlark.Tuple) (starlark.Value, error) {
	return func(_ *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
		var app, expected, target, text, pkg string
		if value, found, _ := api.screen.Get(starlark.String("package")); found {
			app, _ = starlark.AsString(value)
		}
		var x, y, x2, y2 pixel
		delay := 100
		if name == "launch" {
			delay = 700
		}
		parameters := []any{}
		switch name {
		case "tap":
			parameters = append(parameters, "target", &target)
		case "fill":
			parameters = append(parameters, "target", &target, "value", &text)
		case "tap_at", "long_press_at":
			parameters = append(parameters, "x", &x, "y", &y)
		case "swipe":
			parameters = append(parameters, "x", &x, "y", &y, "x2", &x2, "y2", &y2)
		case "text", "input_text":
			parameters = append(parameters, "value", &text)
		case "launch":
			parameters = append(parameters, "package", &pkg)
		case "wait":
			parameters = append(parameters, "delay_ms", &delay)
		}
		parameters = append(parameters, "app?", &app, "expect_text?", &expected)
		if name != "wait" {
			parameters = append(parameters, "delay_ms?", &delay)
		}
		if err := starlark.UnpackArgs(fn.Name(), args, kwargs, parameters...); err != nil {
			return nil, err
		}
		action := starlark.NewDict(10)
		put := func(key string, value starlark.Value) { _ = action.SetKey(starlark.String(key), value) }
		kind := name
		switch name {
		case "tap":
			kind = "tap_target"
		case "tap_at":
			kind = "tap"
		case "long_press_at":
			kind = "long_press"
		case "enter":
			kind = "press_enter"
		}
		put("type", starlark.String(kind))
		put("delay_ms", starlark.MakeInt(delay))
		if app != "" {
			put("expect_package", starlark.String(app))
		}
		if expected != "" {
			put("expect_text", starlark.String(expected))
		}
		switch name {
		case "tap", "fill":
			put("target", starlark.String(target))
		case "tap_at", "long_press_at", "swipe":
			put("x", starlark.Float(x))
			put("y", starlark.Float(y))
		}
		if name == "swipe" {
			put("x2", starlark.Float(x2))
			put("y2", starlark.Float(y2))
		}
		if name == "fill" || name == "text" || name == "input_text" {
			put("text", starlark.String(text))
		}
		if name == "launch" {
			put("package", starlark.String(pkg))
		}
		return starlark.None, api.queue(action)
	}
}

func decodeObject(text string, limit int) (*starlark.Dict, error) {
	if len(text) > limit || !utf8.ValidString(text) || !json.Valid([]byte(text)) {
		return nil, fmt.Errorf("Invalid or oversized JSON object")
	}
	decoder := json.NewDecoder(strings.NewReader(text))
	decoder.UseNumber()
	var raw any
	if err := decoder.Decode(&raw); err != nil {
		return nil, err
	}
	value, err := fromJSON(raw, 0)
	if err != nil {
		return nil, err
	}
	object, ok := value.(*starlark.Dict)
	if !ok {
		return nil, fmt.Errorf("Expected a JSON object")
	}
	return object, nil
}

func fromJSON(raw any, depth int) (starlark.Value, error) {
	if depth > 32 {
		return nil, fmt.Errorf("JSON nesting limit exceeded")
	}
	switch value := raw.(type) {
	case nil:
		return starlark.None, nil
	case bool:
		return starlark.Bool(value), nil
	case string:
		return starlark.String(value), nil
	case json.Number:
		if integer, err := strconv.ParseInt(string(value), 10, 64); err == nil {
			return starlark.MakeInt64(integer), nil
		}
		if !strings.ContainsAny(string(value), ".eE") {
			if integer, ok := new(big.Int).SetString(string(value), 10); ok {
				return starlark.MakeBigInt(integer), nil
			}
		}
		f, err := strconv.ParseFloat(string(value), 64)
		if err != nil || math.IsInf(f, 0) || math.IsNaN(f) {
			return nil, fmt.Errorf("Invalid JSON number")
		}
		return starlark.Float(f), nil
	case []any:
		if depth >= 32 {
			return nil, fmt.Errorf("JSON nesting limit exceeded")
		}
		items := make([]starlark.Value, len(value))
		for i, item := range value {
			v, err := fromJSON(item, depth+1)
			if err != nil {
				return nil, err
			}
			items[i] = v
		}
		return starlark.NewList(items), nil
	case map[string]any:
		if depth >= 32 {
			return nil, fmt.Errorf("JSON nesting limit exceeded")
		}
		dict := starlark.NewDict(len(value))
		keys := make([]string, 0, len(value))
		for key := range value {
			keys = append(keys, key)
		}
		sort.Strings(keys)
		for _, key := range keys {
			v, err := fromJSON(value[key], depth+1)
			if err != nil {
				return nil, err
			}
			if err = dict.SetKey(starlark.String(key), v); err != nil {
				return nil, err
			}
		}
		return dict, nil
	}
	return nil, fmt.Errorf("Unsupported JSON value")
}

// Stream the value ourselves: encoding/json.Marshal builds an unbounded buffer
// before writing. Depth and output budgets also reject cycles and alias expansion.
func encode(value starlark.Value, limit int) (string, error) {
	var out strings.Builder
	write := func(s string) error {
		if out.Len()+len(s) > limit {
			return fmt.Errorf("JSON output limit exceeded")
		}
		out.WriteString(s)
		return nil
	}
	var visit func(starlark.Value, int) error
	visit = func(value starlark.Value, depth int) error {
		if depth > 32 {
			return fmt.Errorf("JSON nesting limit exceeded (possibly cyclic value)")
		}
		switch v := value.(type) {
		case starlark.NoneType:
			return write("null")
		case starlark.Bool:
			if v {
				return write("true")
			}
			return write("false")
		case starlark.String:
			if len(v) > limit {
				return fmt.Errorf("JSON string limit exceeded")
			}
			if !utf8.ValidString(string(v)) {
				return fmt.Errorf("JSON strings must be valid UTF-8")
			}
			b, _ := json.Marshal(string(v))
			return write(string(b))
		case starlark.Int:
			n, ok := v.Int64()
			if !ok {
				return fmt.Errorf("Return large integers as strings")
			}
			return write(strconv.FormatInt(n, 10))
		case starlark.Float:
			f := float64(v)
			if math.IsNaN(f) || math.IsInf(f, 0) {
				return fmt.Errorf("Non-finite JSON number")
			}
			return write(strconv.FormatFloat(f, 'g', -1, 64))
		case *starlark.Dict:
			if depth >= 32 {
				return fmt.Errorf("JSON nesting limit exceeded")
			}
			if err := write("{"); err != nil {
				return err
			}
			for i, item := range v.Items() {
				if i > 0 {
					if err := write(","); err != nil {
						return err
					}
				}
				key, ok := item[0].(starlark.String)
				if !ok {
					return fmt.Errorf("JSON keys must be strings")
				}
				if err := visit(key, depth+1); err != nil {
					return err
				}
				if err := write(":"); err != nil {
					return err
				}
				if err := visit(item[1], depth+1); err != nil {
					return err
				}
			}
			return write("}")
		case *starlark.List:
			if depth >= 32 {
				return fmt.Errorf("JSON nesting limit exceeded")
			}
			if err := write("["); err != nil {
				return err
			}
			for i := 0; i < v.Len(); i++ {
				if i > 0 {
					if err := write(","); err != nil {
						return err
					}
				}
				if err := visit(v.Index(i), depth+1); err != nil {
					return err
				}
			}
			return write("]")
		case starlark.Tuple:
			if depth >= 32 {
				return fmt.Errorf("JSON nesting limit exceeded")
			}
			if err := write("["); err != nil {
				return err
			}
			for i, item := range v {
				if i > 0 {
					if err := write(","); err != nil {
						return err
					}
				}
				if err := visit(item, depth+1); err != nil {
					return err
				}
			}
			return write("]")
		default:
			return fmt.Errorf("Return JSON-compatible values, not %s", value.Type())
		}
	}
	if err := visit(value, 0); err != nil {
		return "", err
	}
	return out.String(), nil
}
