// The bridge accepts and returns UTF-8 JSON byte arrays only. No Java objects,
// references or callbacks enter the interpreter.
package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"fmt"
	"unsafe"

	"github.com/fuba/anautomat/sandbox"
)

//export executeJSON
func executeJSON(data *C.char, size C.int) *C.char {
	var request struct{ Code, Input, Observation, State string }
	var output string
	err := fmt.Errorf("Invalid sandbox request")
	if size > 0 && size <= 400000 && data != nil {
		if decodeErr := json.Unmarshal(C.GoBytes(unsafe.Pointer(data), size), &request); decodeErr == nil {
			output, err = sandbox.Run(request.Code, request.Input, request.Observation, request.State)
		}
	}
	if err != nil {
		message := err.Error()
		if len(message) > 512 {
			message = message[:512]
		}
		encoded, _ := json.Marshal(map[string]string{"error": message})
		output = string(encoded)
	}
	return C.CString(output)
}

func main() {}
