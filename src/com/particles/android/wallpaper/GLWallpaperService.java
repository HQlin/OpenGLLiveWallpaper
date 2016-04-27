/***
 * Excerpted from "OpenGL ES for Android",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material, 
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose. 
 * Visit http://www.pragmaticprogrammer.com/titles/kbogla for more book information.
***/
package com.particles.android.wallpaper;

import com.particles.android.ParticlesRenderer;
import com.particles.android.util.LoggerConfig;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Toast;

public class GLWallpaperService extends WallpaperService {
	@Override
	public Engine onCreateEngine() {
		return new GLEngine();
	}

	/**
	 * ����������Acitivity����
	 * onCreate()������ֽ
	 * onVisibilityChanged()��ֽ��ʾ�����л�����
	 * onDestroy()���ٱ�ֽ
	 */
	public class GLEngine extends Engine {
		private static final String TAG = "GLEngine";

		private WallpaperGLSurfaceView glSurfaceView;
		private ParticlesRenderer particlesRenderer;
		private boolean rendererSet;

		private int width;
		private final GestureDetector gestureDetector;
		private boolean isOnOffsetsChangedWorking, doubleTapEnabled, infiniteScrollingEnabled;
		private ValueAnimator compatValueAnimator;
		private float numberOfPages;

		/**
		 * ȡ��4.0���ϵ���Ч��onOffsetsChanged()������Ч�ķ���
		 * http://andraskindler.com/blog/2015/live-wallpaper-onoffsetchanged-scrolling/
		 */
		private final GestureDetector.SimpleOnGestureListener onGestureListener = new GestureDetector.SimpleOnGestureListener() {

			private float xOffset = 0f;

			@Override
			public boolean onDoubleTap(final MotionEvent e) {
				if (doubleTapEnabled) {
					// startActivity(new Intent(GLWallpaperService.this,
					// MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
					return true;
				}
				return false;
			}

			@Override
			public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
					final float distanceY) {
				if (!isOnOffsetsChangedWorking) {
					final float newXOffset = xOffset + distanceX / width / numberOfPages;
					xOffset = newXOffset;
					if (!infiniteScrollingEnabled) {
						if (newXOffset > 1) {
							xOffset = 1f;
						} else if (newXOffset < 0) {
							xOffset = 0f;
						}
					}
					// translate by xOffset
				}
				return super.onScroll(e1, e2, distanceX, distanceY);
			}

			@Override
			public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
					final float velocityY) {
				if (!isOnOffsetsChangedWorking) {
					float endValue = velocityX > 0 ? (xOffset - (xOffset % (1f / numberOfPages)))
							: (xOffset - (xOffset % (1f / numberOfPages)) + (1 / numberOfPages));

					if (!infiniteScrollingEnabled) {
						if (endValue < 0f) {
							endValue = 0f;
						} else if (endValue > 1f) {
							endValue = 1f;
						}
					}
					compatValueAnimator = ValueAnimator.ofFloat(xOffset, endValue);
					compatValueAnimator.setDuration(150);
					compatValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
						@Override
						public void onAnimationUpdate(final ValueAnimator animation) {
							xOffset = (Float) animation.getAnimatedValue();
							// translate by xOffset
							System.out.println("lin: " + xOffset);
							glSurfaceView.queueEvent(new Runnable() {
								@Override
								public void run() {
									particlesRenderer.handleOffsetsChanged(xOffset, 0.5f);
								}
							});
						}
					});
					compatValueAnimator.start();
				}
				return super.onFling(e1, e2, velocityX, velocityY);
			}
		};

		private GLEngine() {
			super();
			isOnOffsetsChangedWorking = false;

			gestureDetector = new GestureDetector(getBaseContext(), onGestureListener);

			// setup variables
			// doubleTapEnabled = ...
			// infiniteScrollingEnabled = false;
			numberOfPages = 2;
		}

		@Override
		public void onOffsetsChanged(final float xOffset, final float yOffset, final float xOffsetStep,
				final float yOffsetStep, final int xPixelOffset, final int yPixelOffset) {
			// detect if onOffsetsChanged() is working properly
			if (!isOnOffsetsChangedWorking && xOffset != 0.0f && xOffset != 0.5f) {
				isOnOffsetsChangedWorking = true;
			}
			if (isOnOffsetsChangedWorking) {
				// translate by xOffset
				glSurfaceView.queueEvent(new Runnable() {
					@Override
					public void run() {
						particlesRenderer.handleOffsetsChanged(xOffset, yOffset);
					}
				});
			}
		}

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			if (LoggerConfig.ON) {
				Log.d(TAG, "onCreate(" + surfaceHolder + ")");
			}
			glSurfaceView = new WallpaperGLSurfaceView(GLWallpaperService.this);

			// Check if the system supports OpenGL ES 2.0.
			ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
			ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
			// Even though the latest emulator supports OpenGL ES 2.0,
			// it has a bug where it doesn't set the reqGlEsVersion so
			// the above check doesn't work. The below will detect if the
			// app is running on an emulator, and assume that it supports
			// OpenGL ES 2.0.

			final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000
					// Check for emulator.
					|| (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
							&& (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.startsWith("unknown")
									|| Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator")
									|| Build.MODEL.contains("Android SDK built for x86")));

			/*
			 * final ParticlesRenderer particlesRenderer = new
			 * ParticlesRenderer(GLWallpaperService.this);
			 */
			particlesRenderer = new ParticlesRenderer(GLWallpaperService.this);

			if (supportsEs2) {
				glSurfaceView.setEGLContextClientVersion(2);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					//����EGL�����ģ�onPause()��onResume()�������»��ƣ��洢��֡���ݴӶ���ʡ���ܶ��ֲ�Ӱ���û�����
					glSurfaceView.setPreserveEGLContextOnPause(true);
				}
				glSurfaceView.setRenderer(particlesRenderer);
				rendererSet = true;
			} else {
				/*
				 * This is where you could create an OpenGL ES 1.x compatible
				 * renderer if you wanted to support both ES 1 and ES 2. Since
				 * we're not doing anything, the app will crash if the device
				 * doesn't support OpenGL ES 2.0. If we publish on the market,
				 * we should also add the following to AndroidManifest.xml:
				 * 
				 * <uses-feature android:glEsVersion="0x00020000"
				 * android:required="true" />
				 * 
				 * This hides our app from those devices which don't support
				 * OpenGL ES 2.0.
				 */
				Toast.makeText(GLWallpaperService.this, "This device does not support OpenGL ES 2.0.",
						Toast.LENGTH_LONG).show();
				return;
			}

			WindowManager wm = (WindowManager) getBaseContext().getSystemService(Context.WINDOW_SERVICE);
			width = wm.getDefaultDisplay().getWidth();
			setTouchEventsEnabled(true);
			System.out.println("lin: " + wm.getDefaultDisplay().getFlags());
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			if (LoggerConfig.ON) {
				Log.d(TAG, "onVisibilityChanged(" + visible + ")");
			}
			if (rendererSet) {
				if (visible) {
					glSurfaceView.onResume();
				} else {
					glSurfaceView.onPause();
				}
			}
		}

		@Override
		public void onTouchEvent(MotionEvent event) {
			// TODO Auto-generated method stub
			gestureDetector.onTouchEvent(event);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			if (LoggerConfig.ON) {
				Log.d(TAG, "onDestroy()");
			}
			glSurfaceView.onWallpaperDestroy();
		}

		/**
		 * ΪWallpaperService.Engine����getHolder()
		 * ���ٱ�ֽʱ����onWallpaperDestroy()
		 */
		class WallpaperGLSurfaceView extends GLSurfaceView {
			private static final String TAG = "WallpaperGLSurfaceView";

			WallpaperGLSurfaceView(Context context) {
				super(context);

				if (LoggerConfig.ON) {
					Log.d(TAG, "WallpaperGLSurfaceView(" + context + ")");
				}
			}

			@Override
			public SurfaceHolder getHolder() {
				if (LoggerConfig.ON) {
					Log.d(TAG, "getHolder(): returning " + getSurfaceHolder());
				}
				//WallpaperService.Engine.getSurfaceHolder()
				return getSurfaceHolder();
			}

			public void onWallpaperDestroy() {
				if (LoggerConfig.ON) {
					Log.d(TAG, "onWallpaperDestroy()");
				}
				//GLSurfaceView��������������onAttachedToWindow()���Ӧ������GLSurfaceView����ǰ����ع�����
				super.onDetachedFromWindow();
			}
		}

		@Override
		public void onSurfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
			super.onSurfaceChanged(holder, format, width, height);
			this.width = width;
		}
	}
}
