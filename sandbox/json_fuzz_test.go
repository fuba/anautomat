package sandbox

import (
	"encoding/json"
	"testing"
)

// Fixed-data conversion only: fuzzing arbitrary scripts must run in disposable
// processes, not in the credential-bearing application or the host test runner.
func FuzzJSONConversion(f *testing.F) {
	for _, seed := range []string{`{}`, `{"x":[1,true,null,"日本語 😀"]}`, `{"x":1e309}`, `{"x":{"y":{}}}`, `{"x":123456789012345678901234567890}`, `{"x":"\u0000"}`} {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, text string) {
		if len(text) > maxInput {
			return
		}
		value, err := decodeObject(text, maxInput)
		if err != nil {
			return
		}
		encoded, err := encode(value, maxOutput)
		if err != nil {
			return
		}
		if len(encoded) > maxOutput || !json.Valid([]byte(encoded)) {
			t.Fatal("Invalid bounded JSON output")
		}
	})
}
