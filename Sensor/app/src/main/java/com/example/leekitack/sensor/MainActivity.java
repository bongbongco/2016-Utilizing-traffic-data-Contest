package com.example.leekitack.sensor;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Voice_Text = (TextView)findViewById(R.id.voice);
        notify_Text = (TextView)findViewById(R.id.notify);
        timer_Text = (TextView)findViewById(R.id.timer);

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");

        sensormanager = (SensorManager)getSystemService(SENSOR_SERVICE);    //시스템 서비스로부터 SensorManager 인스턴스 얻어옴
        sensor = sensormanager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        onStart();
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
}
