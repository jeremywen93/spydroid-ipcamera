/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.spydroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import net.majorkernelpanic.libmp4.MP4Parser;
import net.majorkernelpanic.libmp4.StsdBox;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

/* 
 * The purpose of this class is to test H.264 support on the phone
 * and to find H.264 pps ans sps parameters. They are needed to 
 * correctly decode the stream.
 * 
 */

public class TestH264 {

	/* Launches the test */
	public static void RunTest(File cacheDir,SurfaceHolder holder, int resX, int resY, int fps, Callback cb) {

		if (test == null) test = new TestH264();
		
		test.cb = cb;
		test.cacheDir = cacheDir;
		test.resX = resX;
		test.resY = resY;
		test.fps = fps;
		test.holder = holder;

		test.start();
		
	}
	
	/* If test succesful, onSuccess is called and provides H264 settings on the phone  */
	public interface Callback {
		public void onStart();
		public void onError(String error);
		public void onSuccess(String result);
	}
	
	private final String TESTFILE = "net.mpk.spydroid-test.mp4";
	
	private static TestH264 test = null;
	private SurfaceHolder.Callback shcb;
	private SurfaceHolder holder;
	private MediaRecorder mr = new MediaRecorder();
	private Callback cb;
	private File cacheDir;
	private int resX, resY, fps;
	private Handler handler = new Handler();
	private Runnable runnable;
	private boolean recording = false;
	
	private TestH264() { }

	private void start() {
		
		if (shcb==null) {
			
			shcb = new SurfaceHolder.Callback() {

	    		@Override
				public void surfaceChanged(SurfaceHolder holder, int format,
						int width, int height) {
	    			
				}
	
	    		@Override
				public void surfaceCreated(SurfaceHolder holder) {
	    			runTest();
				}
	
	    		@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
	    			error("Test cancelled !");
				}
	    		
	    	};
    	
	    	holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    	holder.addCallback(shcb);
	    	
		}
		
	}
	
	private void runTest() {
		
		cb.onStart();
		
		/* 1 - Set up MediaRecorder */
		mr.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		mr.setVideoFrameRate(fps);
		mr.setVideoSize(resX, resY);
		mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mr.setPreviewDisplay(holder.getSurface());

		mr.setOutputFile(cacheDir.getPath()+'/'+TESTFILE);
		
		try {
			mr.prepare();
		} catch (IOException e) {
			error("Can't record video, H.264 not supported ?");
		}
		
		/* 2 - Record dummy video for 500 msecs */
		mr.start(); recording = true;
		
		runnable = new Runnable() {

			@Override
			public void run() {
				
				mr.stop(); recording = false;
				
				/* 3 - Parse video with MP4Parser */
				File file = new File(cacheDir.getPath()+'/'+TESTFILE);
				RandomAccessFile raf = null;
				try {
					raf = new RandomAccessFile(file, "r");
				} catch (FileNotFoundException e1) {
					error("Can't load dummy video");
				}
				
				MP4Parser parser = null;
				try {
					parser = new MP4Parser(raf);
				} catch (IOException e2) {
					error(e2.getMessage());
				}
				
				/* 4 - Get stsd box (contains h.264 parameters) */
				StsdBox stsd = null;
				try {
					stsd = parser.getStsdBox();
				} catch (IOException e1) {
					error(e1.getMessage());
				}
				
				try {
					raf.close();
				} catch (IOException e) {
					error("Error :(");
				}

				if (!file.delete()) Log.e(SpydroidActivity.LOG_TAG,"Temp file not erased");
				
				success(stsd.getProfileLevel()+":"+stsd.getB64PPS()+":"+stsd.getB64SPS()); 	
				
			}
			
			
		};
		
		handler.postDelayed(runnable, 500);
		
	}
	
	private void success(String result) {
		clean();
		cb.onSuccess(result);
	}
	
	private void error(String error) {
		clean();
		cb.onError(error);
	}
	
	private void clean () {
		if (recording) mr.stop();
		recording = false;
		handler.removeCallbacks(runnable);
		holder.removeCallback(shcb);
		shcb = null;
	}
	
	
}