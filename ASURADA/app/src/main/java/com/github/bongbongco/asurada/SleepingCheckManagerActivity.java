package com.github.bongbongco.asurada;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.github.bongbongco.asurada.ui.camera.CameraSourcePreview;
import com.github.bongbongco.asurada.ui.camera.GraphicOverlay;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;
import java.util.ArrayList;


public final class SleepingCheckManagerActivity extends AppCompatActivity implements SensorEventListener {

    //안면 인식 관련 변수
    private static final String TAG = "FaceTracker";
    private static int ALARM_POINT = 0;

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private static final int RC_HANDLE_GMS = 9001;
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    //sensor 관련 변수
    //가속도 센서 값
    private float plus_x;
    private float plus_y;
    private float plus_z;
    //음성 인식 리스너
    private SpeechRecognizer speechRecognizer;
    private Intent intent;
    //음성 인식 결과
    private String Answer;
    //이전 가속도 센서값
    private float pre_plus_x;
    private float pre_plus_y;
    private float pre_plus_z;
    //충돌 감지 여부
    private boolean flag_Crash=false;
    //센서 매니저
    private SensorManager sensormanager;
    private Sensor sensor;
    //타이머 시간
    private String timer;
    private TimerThread timerthread;

    //TextView
    private TextView Voice_Text;
    private TextView notify_Text;
    private TextView timer_Text;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        //안면 인식 관련 변수
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        //sensor 관련 변수
        Voice_Text = (TextView)findViewById(R.id.voice);
        notify_Text = (TextView)findViewById(R.id.notify);
        timer_Text = (TextView)findViewById(R.id.timer);

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");

        sensormanager = (SensorManager)getSystemService(SENSOR_SERVICE);    //시스템 서비스로부터
        sensor = sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        onStart();

        //권한 승인 여부 화인 후 카메라 객체 생성
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    public void onStart()
    {
        super.onStart();
        if(sensor!=null)
        {
            sensormanager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);  //리스너 등록
        }
    }

    public void onStop()
    {
        super.onStop();
        if(sensor!=null)
        {
            sensormanager.unregisterListener(this); //리스너 해제
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy){}

    public void onSensorChanged(SensorEvent event)  //센서값이 변하면 자동으로 호출되는 함수
    {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            plus_x = event.values[0];
            plus_y = event.values[1];
            plus_z = event.values[2];
            if((Math.abs(pre_plus_x-plus_x)>25||Math.abs(pre_plus_y-plus_y)>25||Math.abs(pre_plus_z-plus_z)>25) && !flag_Crash)   //충돌 감지 이벤트 발생 영역
            {
                flag_Crash = true;  // 한번 충돌 후 flag 값 변경

                notify_Text.setText("충돌이 감지 되었습니다.");

                timerthread = new TimerThread(20); //타이머 스레드 생성 , 현재 대기시간은 20초로 지정

                timerthread.start();    //스레드 시작

                ToReport(); //음성 인식 시작

            }
            //속도가 얼마 이상 일 때 감지가 필요할 경우 사용// speed = Math.abs(plus_x+plus_y+plus_z -pri_plus_x-pri_plus_y-pri_plus_z)/gabOfTime * 10000;

            //현재 센서값을 이전 센서값에 저장.
            pre_plus_x = plus_x;
            pre_plus_y = plus_y;
            pre_plus_z = plus_z;

            //받아온 센서값의 로그 값을 찍습니다.
            Log.d("+x",String.valueOf(plus_x));
            Log.d("+y",String.valueOf(plus_y));
            Log.d("+z", String.valueOf(plus_z));
        }
    }

    public void ToReport()
    {
        //리스너 등록 후 이벤트 기다림
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(listener);
        speechRecognizer.startListening(intent);
    }

    private RecognitionListener listener = new RecognitionListener() {  //음성 인식 리스너
        @Override
        public void onReadyForSpeech(Bundle bundle) {

            Toast.makeText(getApplicationContext(), "음성 인식 준비 완료", Toast.LENGTH_LONG).show();
        }
        @Override
        public void onBeginningOfSpeech() {}
        @Override
        public void onRmsChanged(float v) {}
        @Override
        public void onBufferReceived(byte[] bytes) {}
        @Override
        public void onEndOfSpeech() {}
        @Override
        public void onError(int i) {
            Toast.makeText(getApplicationContext(), "에러 발생",Toast.LENGTH_LONG).show();
            if(flag_Crash) {
                speechRecognizer.startListening(intent);
            }
        }

        @Override
        public void onResults(Bundle bundle) {      //음성 인식 결과를 얻어오는 함수 오버라이딩
            String key = "";
            key = SpeechRecognizer.RESULTS_RECOGNITION;
            ArrayList<String> mResult = bundle.getStringArrayList(key);
            String[] rs = new String[mResult.size()];
            mResult.toArray(rs);

            Answer = rs[0];
            Voice_Text.setText(Answer);

            if(Answer.equals("아니요") )    // "아니요" 인식 한 경우 신고 대기 취소
            {
                notify_Text.setText("사고 신고 대기를 취소합니다. 다시 충돌을 감지합니다.");
                flag_Crash = false;
                timerthread.flag = false;
                speechRecognizer.stopListening();

            }
            else if(Answer.equals("신고해"))   //"신고해" 인식 한 경우
            {
                notify_Text.setText("사고 신고를 진행합니다.");
                flag_Crash = false;
                timerthread.flag = false;
            }
            else {                              //인식이 제대로 안 된 경우
                Voice_Text.setText("다시 인식합니다.");
                speechRecognizer.startListening(intent);
            }
        }

        @Override
        public void onPartialResults(Bundle bundle){}

        @Override
        public void onEvent(int i, Bundle bundle) {}
    };

    class TimerThread extends Thread { //시간을 재기 위한 스레드

        int delay_time;
        boolean flag = true;
        int current_time = 0;
        TimerHandler myhandler;

        public TimerThread(int delay_time) {
            this.delay_time = delay_time * 1000;
            myhandler = new TimerHandler();
        }

        public void run() {

            while (flag) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ++current_time;
                if (current_time * 1000 == delay_time) {
                    Message msg = myhandler.obtainMessage(0,current_time,0);
                    myhandler.sendMessage(msg);
                } else {

                    Message msg = myhandler.obtainMessage(1,current_time,0);
                    myhandler.sendMessage(msg);
                }
            }

        }
    }
    class TimerHandler extends Handler{     //스레드와 메인 프로세스 연결하기 위한 핸들러
        public void handleMessage(android.os.Message msg)
        {
            if(msg.what == 0)
            {
                timerthread.flag = false;
                notify_Text.setText("차량 사고 신고를 진행합니다.");
                timer_Text.setText(String.valueOf(msg.arg1));
                speechRecognizer.stopListening();
                flag_Crash = false;

            }
            else if(msg.what==1)
            {

                timer_Text.setText(String.valueOf(msg.arg1));
            }

        }
    }

    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }


    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }


    private class GraphicFaceTracker extends Tracker<Face> {
        Context context = getApplicationContext();
        int count = 0;
        SoundPool pool = null;
        int sound =0;

        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
            this.pool = new SoundPool(1, 3, 0);
            this.sound = this.pool.load(context, R.raw.gunshot,1);
        }

        private boolean SleepingFaceCheck(float leftEye, float rightEye)
        {
            boolean bool = true;
            if ((leftEye != Face.UNCOMPUTED_PROBABILITY && leftEye <= 0.1F)
                    || (rightEye != Face.UNCOMPUTED_PROBABILITY && rightEye <= 0.1F)) {
                bool = false;
            }
            return bool;
        }

        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            float leftEye = face.getIsLeftEyeOpenProbability();
            float rightEye = face.getIsRightEyeOpenProbability();
            do
            {
                if (SleepingFaceCheck(leftEye, rightEye)) {
                    return;
                }
                Log.e("MainActivity", "Close count -> " + this.count + " left -> " + face.getIsLeftEyeOpenProbability() + ", right -> " + face.getIsRightEyeOpenProbability());
                this.count += 1;
            } while (this.count != 4);
            this.pool.play(this.sound, 5.0F, 5.0F, 1, 0, 1.0F);
            this.count = 0;
            return;
        }

        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}
