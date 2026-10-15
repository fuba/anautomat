package sandbox

import (
	"encoding/json"
	"strings"
	"testing"
)

func run(t *testing.T, code string) map[string]any {
	t.Helper()
	text, err := Run(code, `{"values":[2,3,5]}`, `{"package":"com.example.app","width":900}`, `{"n":4}`)
	if err != nil {
		t.Fatal(err)
	}
	var result map[string]any
	if err = json.Unmarshal([]byte(text), &result); err != nil {
		t.Fatal(err)
	}
	return result
}

func TestCodeStateAndQueuedUI(t *testing.T) {
	result := run(t, `def main():
    total = 0
    for n in input["values"]:
        total += n
    state["n"] += 1
    log("sum=" + str(total))
    ui.fill("Result", str(total))
    ui.tap("Verify", expect_text="Ready", delay_ms=300)
    return {"total": total}
`)
	if result["result"].(map[string]any)["total"] != float64(10) || result["state"].(map[string]any)["n"] != float64(5) || result["logs"] != "sum=10\n" {
		t.Fatal(result)
	}
	actions := result["actions"].([]any)
	if len(actions) != 2 {
		t.Fatal(actions)
	}
	first := actions[0].(map[string]any)
	second := actions[1].(map[string]any)
	if first["type"] != "fill" || first["target"] != "Result" || first["text"] != "10" || first["expect_package"] != "com.example.app" || second["type"] != "tap_target" || second["delay_ms"] != float64(300) {
		t.Fatal(actions)
	}
}

func TestUICommands(t *testing.T) {
	result := run(t, `def main():
    ui.tap_at(1.5, 2)
    ui.long_press_at(3, 4, delay_ms=200)
    ui.swipe(1, 2, 3, 4)
    ui.text("replace")
    ui.input_text("append")
    ui.enter()
    ui.launch("another.app")
    ui.fill("id:another.app:id/name", "value", app="another.app")
    ui.back()
    ui.home()
    ui.recents()
    ui.notifications()
    ui.quick_settings()
    ui.wait(500)
    return None
`)
	actions := result["actions"].([]any)
	if len(actions) != 14 {
		t.Fatal(actions)
	}
	if actions[0].(map[string]any)["x"] != 1.5 {
		t.Fatal(actions)
	}
	if actions[6].(map[string]any)["delay_ms"] != float64(700) || actions[7].(map[string]any)["expect_package"] != "another.app" || actions[8].(map[string]any)["expect_package"] != "com.example.app" {
		t.Fatal(actions)
	}
}

func TestQueueCopiesMutableActions(t *testing.T) {
	result := run(t, `def main():
    action = {"type":"home"}
    macro([action])
    action["type"] = "shell"
    return True
`)
	if result["actions"].([]any)[0].(map[string]any)["type"] != "home" {
		t.Fatal(result)
	}
}

func TestUTF8AndJSONValues(t *testing.T) {
	result := run(t, "def main():\n    print(\"日本語 😀\")\n    return [\"\\x00\", \"日本語 😀\", True, None, 1.5, (2, 3)]\n")
	if result["logs"] != "日本語 😀\n" || result["result"].([]any)[0] != "\x00" {
		t.Fatal(result)
	}
	large := run(t, `def main():
    return str(12345678901234567890 * 12345678901234567890)
`)
	if large["result"] != "152415787532388367501905199875019052100" {
		t.Fatal(large)
	}
}

func TestRejectProgramsAndQuotas(t *testing.T) {
	for name, code := range map[string]string{
		"load":                 `load("os", "system")` + "\ndef main():\n    return 1\n",
		"import":               "import os\ndef main():\n    return 1\n",
		"file":                 "def main():\n    return open(\"/data/data/dev.androiduse\")\n",
		"network":              "def main():\n    return socket(\"example.com\")\n",
		"java":                 "def main():\n    return Java.type(\"java.lang.Runtime\")\n",
		"reflection":           "def main():\n    return getattr(ui, \"getClass\")\n",
		"while":                "def main():\n    while True:\n        pass\n",
		"recursion":            "def main():\n    return main()\n",
		"steps":                "def main():\n    for i in range(1000000):\n        pass\n",
		"input immutable":      "def main():\n    input[\"x\"] = 1\n",
		"invalid UTF8":         "def main():\n    return \"日本語\"[0]\n",
		"screen immutable":     "def main():\n    screen[\"package\"] = \"other.app\"\n",
		"source":               "def main():\n    return 1\n" + strings.Repeat(" ", maxCode),
		"logs":                 "def main():\n    log(\"x\" * 8193)\n",
		"print quota":          "def main():\n    print(\"x\" * 8193)\n",
		"state":                "def main():\n    state[\"x\"] = \"x\" * 8193\n",
		"output":               "def main():\n    return \"x\" * 65537\n",
		"actions":              "def main():\n    for i in range(33):\n        ui.home()\n",
		"combined action size": "def main():\n    ui.text(\"x\" * 16400)\n    ui.text(\"x\" * 16400)\n",
		"macro scalar":         "def main():\n    macro([1])\n",
		"macro empty":          "def main():\n    macro([])\n",
		"failure after queue":  "def main():\n    ui.home()\n    fail(\"no effects\")\n",
		"cycle":                "def main():\n    value = []\n    value.append(value)\n    return value\n",
		"alias expansion":      "def main():\n    value = [0]\n    for i in range(30):\n        value = [value, value]\n    return value\n",
		"nonstring keys":       "def main():\n    return {1: 2}\n",
		"nonfinite":            "def main():\n    return float(\"nan\")\n",
		"large integer":        "def main():\n    return 2 ** 100\n",
		"function result":      "def main():\n    return main\n",
		"unknown UI":           "def main():\n    ui.shell(\"id\")\n",
		"wrong argument":       "def main():\n    ui.tap_at(\"x\", 2)\n",
		"missing main":         "answer = 42\n",
	} {
		t.Run(name, func(t *testing.T) {
			text, err := Run(code, `{}`, `{}`, `{}`)
			if err == nil || text != "" {
				t.Fatalf("Accepted rejected program: %s", text)
			}
		})
	}
}

func TestInputBounds(t *testing.T) {
	boundary := `{"x":` + strings.Repeat("[", 31) + "0" + strings.Repeat("]", 31) + "}"
	if _, err := Run("def main():\n    return 0\n", boundary, `{}`, `{}`); err != nil {
		t.Fatalf("Rejected bounded nesting: %v", err)
	}
	if _, err := Run("def main():\n    input[\"values\"].append(2)\n", `{"values":[1]}`, `{}`, `{}`); err == nil {
		t.Fatal("Nested input was mutable")
	}
	text, err := Run("def main():\n    return str(input[\"n\"] + 1)\n", `{"n":12345678901234567890}`, `{}`, `{}`)
	if err != nil || !strings.Contains(text, "12345678901234567891") {
		t.Fatalf("Lost integer precision: %s %v", text, err)
	}
	for _, input := range []string{`[]`, `null`, `{"a":NaN}`, `{` + strings.Repeat(`"x":1,`, 4000) + `"y":2}`, `{"x":` + strings.Repeat("[", 33) + "0" + strings.Repeat("]", 33) + "}"} {
		if _, err := Run("def main():\n    return 0\n", input, `{}`, `{}`); err == nil {
			t.Fatalf("Accepted %d input bytes", len(input))
		}
	}
}
