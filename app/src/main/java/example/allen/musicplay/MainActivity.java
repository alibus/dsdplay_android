package example.allen.musicplay;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.justcodecs.dsd.DSDFormat;
import org.justcodecs.dsd.DSFFormat;
import org.justcodecs.dsd.Decoder;
import org.justcodecs.dsd.Utils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "dsfPlay";

    private static String[] PERMISSIONS_STORAGE = { "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE" };

    AudioTrack player;

    private String recordingFile;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btn = (Button)findViewById(R.id.start_play);
        verifyStoragePermissions(this);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayThread mThread = new PlayThread(getPCMFormat(),"/sdcard/a.dsf");
                mThread.start();
            }
        });

    }

    public static void verifyStoragePermissions(Activity activity) {
        try { //检测是否有写的权限

            int permission = ActivityCompat.checkSelfPermission(activity, "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    Decoder createDecoder() {
        return new Decoder();
    }


    Decoder.PCMFormat getPCMFormat(){
        Decoder.PCMFormat pcmf = new Decoder.PCMFormat();
        pcmf.sampleRate = 176400;
        //System.out.printf("clip: %x %x  %x-%x%n",((1 << pcmf.bitsPerSample) - 1) >> 1, 1 << pcmf.bitsPerSample, Short.MAX_VALUE, Short.MIN_VALUE);
        pcmf.channels = 2;
        pcmf.bitsPerSample = 16;
        return pcmf;
    }


    class PlayThread extends Thread{

        String url;
        Decoder.PCMFormat pcmf;

        boolean bStop = false;


        public PlayThread(Decoder.PCMFormat pcmf,String url){
            this.pcmf = pcmf;
            this.url = url;

        }

        @Override
        public void run() {
            play(pcmf,url);
        }

        public void play(Decoder.PCMFormat pcmf, String url) {

            Decoder decoder = createDecoder();
            try {
                DSDFormat<?> dsd = null;
                if (url.endsWith(".dsf")) {
                    dsd = new DSFFormat();
                    Log.e(TAG,"DSFFormat start");
                    dsd.init(new Utils.RandomDSDStream(new File(url)));
                    decoder.init(dsd);
                }
            } catch (Decoder.DecodeException e) {
                e.printStackTrace();
                Log.e(TAG,"DecodeException =--"+e.getMessage());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG,"file not found");
                return;
            }
            try {
                //最小缓存区
                int bufferSizeInBytes = AudioTrack.getMinBufferSize(pcmf.sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                Log.e(TAG,"bufferSizeInBytes + "+bufferSizeInBytes);
                if(player == null){
                    player = new AudioTrack(AudioManager.STREAM_MUSIC, pcmf.sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM);
                }
                //创建AudioTrack对象   依次传入 :流类型、采样率（与采集的要一致）、音频通道（采集是IN 播放时OUT）、量化位数、最小缓冲区、模式
                int[][] samples = new int[pcmf.channels][bufferSizeInBytes];
                int channels = (pcmf.channels > 2 ? 2 : pcmf.channels);
                int bytesChannelSample = pcmf.bitsPerSample / 8;
                int bytesSample = channels * bytesChannelSample;
                decoder.setPCMFormat(pcmf);
                decoder.seek(0);
                byte[] playBuffer = new byte[bytesSample * bufferSizeInBytes];
                player.play();//开始播放
                while (!bStop) {
                    int nsampl = decoder.decodePCM(samples);

                    if (nsampl <= 0)
                        break;
                    int bp = 0;
                    for (int s = 0; s < nsampl; s++) {
                        for (int c = 0; c < 1; c++) {
                            samples[c][s] >>=8;
                            for (int b = 0; b < bytesChannelSample; b++)
                                playBuffer[bp++] = (byte) ((samples[c][s] >> (b * 8)) & 255);
                        }
                    }

                    bp = 0;

                    for(int s = 0; s < nsampl; s++){
                        playBuffer[bp++] = (byte)samples[0][s];
                    }

                    Log.e(TAG,"nsampl --"+nsampl+"===bp="+bp);



                    player.write(playBuffer, 0, bp);
                }
            }catch (Decoder.DecodeException e){
                e.printStackTrace();
                Log.e(TAG,"DecodeException 2  =--"+e.getMessage());
            }

            player.stop();//停止播放
            player.release();//释放资源
            player = null;

        }

        public void playStop(){
            bStop = true;

        }

    }



}
