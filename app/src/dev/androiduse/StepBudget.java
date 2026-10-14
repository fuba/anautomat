package dev.androiduse;

final class StepBudget {
 private int used;
 boolean exhausted(int limit){return used>=limit;}
 void consume(){used++;}
 void renew(){used=0;}
}
