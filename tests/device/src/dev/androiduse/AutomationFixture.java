package dev.androiduse;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** A standalone, offline target for real accessibility/LLM tests, never shipped in anautomat. */
public final class AutomationFixture extends Activity {
 public void onCreate(Bundle state){super.onCreate(state);LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(36,64,36,36);layout.setBackgroundColor(0xfff7f7f5);
  TextView title=new TextView(this);title.setText("Java + macro test");title.setTextColor(0xff161616);title.setTextSize(26);layout.addView(title);
  TextView instruction=new TextView(this);instruction.setText("Compute the sum of squares from 1 through 100 in Java. Enter the result, then verify.");instruction.setTextColor(0xff161616);instruction.setTextSize(18);layout.addView(instruction);
  EditText input=new EditText(this);input.setHint("Result");input.setSingleLine(true);input.setTextColor(0xff161616);input.setTextSize(24);layout.addView(input,new LinearLayout.LayoutParams(-1,100));
  TextView result=new TextView(this);result.setText("WAITING");result.setTextSize(24);result.setTextColor(0xff161616);result.setGravity(Gravity.CENTER);
  Button verify=new Button(this);verify.setText("Verify");verify.setOnClickListener(v->result.setText(input.getText().toString().trim().equals("338350")?"PASS: 338350":"FAIL"));layout.addView(verify,new LinearLayout.LayoutParams(-1,100));layout.addView(result);setContentView(layout);
 }
}
