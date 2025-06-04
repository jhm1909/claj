package com.xpdustry.claj.server.util;

import arc.math.WindowedMean;
import arc.util.Time;


/** Calculate network speed. */
public class NetworkSpeed {
  protected final WindowedMean upload, download;
  protected long lastUpload, lastDownload, uploadAccum, downloadAccum;
  
  public NetworkSpeed(int windowSec) {
    upload = new WindowedMean(windowSec);
    download = new WindowedMean(windowSec);
  }
  
  public void addDownloadMark(int bufferRemaining) {
    if (Time.timeSinceMillis(lastDownload) >= 1000) {
      lastDownload = Time.millis();
      download.add(downloadAccum);
      downloadAccum = 0;
    }
    downloadAccum += bufferRemaining;
  }
  
  public void addUploadMark(int bufferWritten) {
    if (Time.timeSinceMillis(lastUpload) >= 1000) {
      lastUpload = Time.millis();
      upload.add(uploadAccum);
      uploadAccum = 0;
    }
    uploadAccum += bufferWritten;
  }
  
  /** In bytes per second */
  public float downloadSpeed() {
    return download.mean();
  }
  
  /** In bytes per second */
  public float uploadSpeed() {
    return upload.mean();
  }
}
