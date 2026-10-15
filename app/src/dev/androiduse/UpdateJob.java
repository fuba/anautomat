package dev.androiduse;

import android.app.job.*;

public final class UpdateJob extends JobService {
 public boolean onStartJob(JobParameters params){Updates.get(this).check(false,()->jobFinished(params,false));return true;}
 public boolean onStopJob(JobParameters params){return false;}
}
