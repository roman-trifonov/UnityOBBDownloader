package com.unity3d.plugin.downloader;

import android.app.Activity;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.os.Messenger;
import android.content.pm.PackageManager.NameNotFoundException;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.provider.Settings;
import android.os.Build;

import java.io.InputStream;

import com.google.android.vending.expansion.downloader.*;

public class UnityDownloaderActivity extends Activity implements IDownloaderClient
{
    private ProgressBar mPB;
	
    private TextView mStatusText;
    private TextView mProgressFraction;
    private TextView mProgressPercent;
    private TextView mAverageSpeed;
    private TextView mTimeRemaining;
	
    private View mDashboard;
    private View mCellMessage;
	
    private Button mPauseButton;
    private Button mWiFiSettingsButton;
    
    private boolean mStatePaused;
    private int mState;
	
    private IDownloaderService mRemoteService;
	
    private IStub mDownloaderClientStub;
    private static final String LOG_TAG = "OBB";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		try {
			Intent launchIntent = getIntent();
			Class<?> mainActivity = Class.forName(launchIntent.getStringExtra("unityplayer.Activity"));
			Intent intentToLaunchMainActivityFromNotification = new Intent(this, mainActivity);
			intentToLaunchMainActivityFromNotification.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
			intentToLaunchMainActivityFromNotification.setAction("android.intent.action.MAIN");
			intentToLaunchMainActivityFromNotification.addCategory("android.intent.category.LAUNCHER");

            int pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            }

			// Build PendingIntent used to open this activity from Notification
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentToLaunchMainActivityFromNotification, pendingIntentFlag);
			// Request to start the download
			int startResult = DownloaderClientMarshaller.startDownloadServiceIfRequired(this, pendingIntent, UnityDownloaderService.class);
			
			if (startResult != DownloaderClientMarshaller.NO_DOWNLOAD_REQUIRED) {
				// The DownloaderService has started downloading the files, show progress
				initializeDownloadUI();
				return;
			} // otherwise, download not needed so we fall through to starting the movie
		} catch (ClassNotFoundException e) {
			android.util.Log.e(LOG_TAG, "Cannot find own package! MAYDAY!");
			e.printStackTrace();
		} catch (NameNotFoundException e) {
			android.util.Log.e(LOG_TAG, "Cannot find own package! MAYDAY!");
			e.printStackTrace();
		}
		finish();
    }

    /**
     * Connect the stub to our service on resume.
     */
    @Override
    protected void onResume() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }
        super.onResume();
    }
	
    /**
     * Disconnect the stub from our service on stop
     */
    @Override
    protected void onStop() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    private void initializeDownloadUI() {
        mDownloaderClientStub = DownloaderClientMarshaller.CreateStub(this, UnityDownloaderService.class);
        setContentView(Helpers.getLayoutResource(this, "main"));

		// Set the background to the splash image generated by Unity
       	try { 
			InputStream is = getAssets().open("bin/Data/splash.png");
			Bitmap splashBitmap = null;
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			splashBitmap = BitmapFactory.decodeStream(is, null, options);
			is.close();
			ImageView splashImage = (ImageView) findViewById(Helpers.getIdResource(this, "splashImage"));
			splashImage.setImageBitmap(splashBitmap);
		} catch(Exception e) { }
		
        mPB = (ProgressBar) findViewById(Helpers.getIdResource(this, "progressBar"));
        mStatusText = (TextView) findViewById(Helpers.getIdResource(this, "statusText"));
        mProgressFraction = (TextView) findViewById(Helpers.getIdResource(this, "progressAsFraction"));
        mProgressPercent = (TextView) findViewById(Helpers.getIdResource(this, "progressAsPercentage"));
        mAverageSpeed = (TextView) findViewById(Helpers.getIdResource(this, "progressAverageSpeed"));
        mTimeRemaining = (TextView) findViewById(Helpers.getIdResource(this, "progressTimeRemaining"));
        mDashboard = findViewById(Helpers.getIdResource(this, "downloaderDashboard"));
        mCellMessage = findViewById(Helpers.getIdResource(this, "approveCellular"));
        mPauseButton = (Button) findViewById(Helpers.getIdResource(this, "pauseButton"));
        mWiFiSettingsButton = (Button) findViewById(Helpers.getIdResource(this, "wifiSettingsButton"));
		
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStatePaused) {
                    mRemoteService.requestContinueDownload();
                } else {
                    mRemoteService.requestPauseDownload();
                }
                setButtonPausedState(!mStatePaused);
            }
        });
        
        mWiFiSettingsButton.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));                
            }
        });
		
        Button resumeOnCell = (Button) findViewById(Helpers.getIdResource(this, "resumeOverCellular"));
        resumeOnCell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mRemoteService.setDownloadFlags(IDownloaderService.FLAGS_DOWNLOAD_OVER_CELLULAR);
                mRemoteService.requestContinueDownload();
                mCellMessage.setVisibility(View.GONE);
            }
        });
		
    }
    private void setState(int newState) {
        if (mState != newState) {
            mState = newState;
            mStatusText.setText(Helpers.getDownloaderStringResourceIDFromState(this, newState));
        }
    }
	
    private void setButtonPausedState(boolean paused) {
        mStatePaused = paused;
        int stringResourceID = Helpers.getStringResource(this, paused ? "text_button_resume" : "text_button_pause");
        mPauseButton.setText(stringResourceID);
    }
	
	@Override
	public void onServiceConnected(Messenger m) {
        mRemoteService = DownloaderServiceMarshaller.CreateProxy(m);
        mRemoteService.onClientUpdated(mDownloaderClientStub.getMessenger());
	}
	@Override
	public void onDownloadStateChanged(int newState) {
        setState(newState);
        boolean showDashboard = true;
        boolean showCellMessage = false;
        boolean paused;
        boolean indeterminate;
        switch (newState) {
            case IDownloaderClient.STATE_IDLE:
                // STATE_IDLE means the service is listening, so it's
                // safe to start making calls via mRemoteService.
                paused = false;
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_CONNECTING:
            case IDownloaderClient.STATE_FETCHING_URL:
                showDashboard = true;
                paused = false;
                indeterminate = true;
                break;
            case IDownloaderClient.STATE_DOWNLOADING:
                paused = false;
                showDashboard = true;
                indeterminate = false;
                break;
				
            case IDownloaderClient.STATE_FAILED_CANCELED:
            case IDownloaderClient.STATE_FAILED:
            case IDownloaderClient.STATE_FAILED_FETCHING_URL:
            case IDownloaderClient.STATE_FAILED_UNLICENSED:
                paused = true;
                showDashboard = false;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_NEED_CELLULAR_PERMISSION:
            case IDownloaderClient.STATE_PAUSED_WIFI_DISABLED_NEED_CELLULAR_PERMISSION:
                showDashboard = false;
                paused = true;
                indeterminate = false;
                showCellMessage = true;
                break;
            case IDownloaderClient.STATE_PAUSED_BY_REQUEST:
                paused = true;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_PAUSED_ROAMING:
            case IDownloaderClient.STATE_PAUSED_SDCARD_UNAVAILABLE:
                paused = true;
                indeterminate = false;
                break;
            case IDownloaderClient.STATE_COMPLETED:
                showDashboard = false;
                paused = false;
                indeterminate = false;
				finish();
                return;
            default:
                paused = true;
                indeterminate = true;
                showDashboard = true;
        }
        int newDashboardVisibility = showDashboard ? View.VISIBLE : View.GONE;
        if (mDashboard.getVisibility() != newDashboardVisibility) {
            mDashboard.setVisibility(newDashboardVisibility);
        }
        int cellMessageVisibility = showCellMessage ? View.VISIBLE : View.GONE;
        if (mCellMessage.getVisibility() != cellMessageVisibility) {
            mCellMessage.setVisibility(cellMessageVisibility);
        }
        mPB.setIndeterminate(indeterminate);
        setButtonPausedState(paused);
	}
	@Override
	public void onDownloadProgress(DownloadProgressInfo progress) {
        mAverageSpeed.setText(getString(Helpers.getStringResource(this, "kilobytes_per_second"),
										Helpers.getSpeedString(progress.mCurrentSpeed)));
        mTimeRemaining.setText(getString(Helpers.getStringResource(this, "time_remaining"),
										 Helpers.getTimeRemaining(progress.mTimeRemaining)));
		
        progress.mOverallTotal = progress.mOverallTotal;
        mPB.setMax((int) (progress.mOverallTotal >> 8));
        mPB.setProgress((int) (progress.mOverallProgress >> 8));
        mProgressPercent.setText(Long.toString(progress.mOverallProgress
											   * 100 /
											   progress.mOverallTotal) + "%");
        mProgressFraction.setText(Helpers.getDownloadProgressString
								  (progress.mOverallProgress,
								   progress.mOverallTotal));
	}
}
