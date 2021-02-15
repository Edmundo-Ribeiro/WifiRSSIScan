package com.example.wifirssiscan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

//    Lista com os BSSID's (endereço MAC) que desejamos evidenciar nas medidas
    private List<String> knownBssid = Arrays.asList(new String[]{
            "f4:54:20:5d:4c:3f",
            "f4:54:20:5d:4c:3e",
            "c8:5a:9f:e8:e2:c7"
    });


    private WifiManager wifiManager;

    private Button btnScan;
    private Button btnShow;
    private ListView listWifiScan;
    private TextView textCount;
    private TextView sampleCounter;

    //timer para realizar mostrar tempo restante para a proxima medida
    private CountDownTimer timer;


    //flags para estado dos botões
    private boolean started = false;
    private boolean showAll = false;

    //Lista con os resultados de um escanamento wifi
    private List<ScanResult> scanResults;

    //Array para mostrar a lista na tela
    private ArrayList<String> arrayList = new ArrayList<>();
    private ArrayAdapter adapter;

    //objetos necessários para repetir as medidas periodicamente
    private Runnable mRunnable;
    private Handler mHandler = new Handler();

    //tempo entre escaneamentos
    private int timeInterval = 30 *1000;

    //nome do arquivo a ser salvo
    private String filename;

    //contador de quantas amostras foram feitas
    private int samples = 0 ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Referenciar os elementos de UI na tela
        textCount = findViewById(R.id.textCount);
        sampleCounter = findViewById(R.id.sampleCounter);
        btnScan = findViewById(R.id.btnScan);
        btnShow = findViewById(R.id.btnShow);
        listWifiScan = findViewById(R.id.listwifiscan);

        //Timer da tela sendo definido para mudar o texto a cada segundo
        timer = new CountDownTimer(timeInterval, 1000) {

            public void onTick(long millisUntilFinished) {
                textCount.setText( String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                textCount.setText("!");
                this.cancel();
            }
        };

        //Adaptador para colocar lista de resultados na tela
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, arrayList);
        listWifiScan.setAdapter(adapter);

        //Verificação de permissoes sobre o uso do WIFI
        // (talvez tenha um jeito melhor de fazer isso. Já pensei em uma forma, mas ainda falta implementar)
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
            &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
            &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            if ( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_WIFI_STATE)
             ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 1);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 1);
            }
            return;
        }


        //Botão de iniciar/parar escaneamento
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Ao pedir para parar o escanemaneto:
                // mudar texto do botão, interromper repetição de escaneamentos, para timer.
                if(started){
                    btnScan.setText("Start Scan");
                    mHandler.removeCallbacks(mRunnable);
                    timer.onFinish();

                }
                //Ao pedir para iniciar o escanemaneto:


                else {
                    btnScan.setText("Stop Scan"); // mudar texto do botão.
                    mHandler.post(mRunnable); // iniciar repetição de escaneamentos,
                    String nowTime = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(Calendar.getInstance().getTime());
                    filename = nowTime + ".txt"; // definir nome do arquivo a ser criado como a data/hora atual,
                    samples = 0; // iniciar contador de amostras.
                    sampleCounter.setText(String.format("%d", samples));
                }
                started = !started;

            }
        });

        //botão para alterar se será mostrados todas as redes ou apenas as cadastradas
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showAll) {
                    btnShow.setText("Show All");
                } else {
                    btnShow.setText("Show Registered");
                }
                showAll = !showAll;
            }
        });

        //Instanciando o wifi manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        //Se o wifi estiver inativo, ativa-lo
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "WiFi está inativo... ativando ele agora!", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }



        //Bloco de codigo para repetir periodicamente o escaneamento
        mRunnable = new Runnable() {
            @Override
            public void run() {

                scanWifi();
                timer.start();

                mHandler.postDelayed(mRunnable, timeInterval);
            }
        };
    }


    //Metodo para salvar leitura no arquivo
    private void save(String contentToSave) {

        File file = new File(this.getFilesDir(), filename);

        String text = contentToSave.toLowerCase();

        try (FileOutputStream fos = this.openFileOutput(filename, Context.MODE_APPEND)) {
            fos.write(text.getBytes());
            Toast.makeText(this, "Writing done...", Toast.LENGTH_LONG).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //metodo para executar o escaneamento
    private void scanWifi() {
        arrayList.clear(); //limpar lista da tela

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter); //registrar pedido para receber escaneamento wifi
        boolean success = wifiManager.startScan(); // pedir para iniciar escaneamento

        Toast.makeText(this, "Scanning WiFi ...", Toast.LENGTH_SHORT).show();

        //Se algo der errado mostrar Toast na tela
        if (!success) {
            Toast.makeText(this, "Error trying to start WiFi scan ...", Toast.LENGTH_SHORT).show();
        }
        else{
            // incrementar numero de amostras coletadas e atualizar na tela
            samples +=1;
            sampleCounter.setText(String.format("%d", samples));
        }

    }


    //Definir ação do receiver ao receber resultados do escaneamento
    BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);

            //Se conseguiu receber os resultados do escaneamento, mostrar na tela os resultados
            if (success) {
                scanResults = wifiManager.getScanResults();
                if(this.isOrderedBroadcast()) {
                    unregisterReceiver(this);
                }
                for (ScanResult scanResult : scanResults) {
                    if(!showAll) {
                        if (knownBssid.contains(scanResult.BSSID)) {
                            arrayList.add("BSSID: " + scanResult.BSSID + " - " + scanResult.SSID + " - RSSI: " + scanResult.level + "dBm - " + scanResult.timestamp);
                            adapter.notifyDataSetChanged();
                            //salvar resultado no arquivo separando informações com ';'
                            save("BSSID ; " + scanResult.BSSID + ";" + scanResult.SSID + " ; RSSI ;" + scanResult.level + ";" + scanResult.timestamp+ "\n");
                        }
                    }
                    else{
                        arrayList.add("BSSID: " + scanResult.BSSID + " - " + scanResult.SSID + " - RSSI: " + scanResult.level + "dBm - " + scanResult.timestamp);
                        adapter.notifyDataSetChanged();
                    }
                }

            } else {
                // Mostrar que algo deu errado
                Toast.makeText(getApplicationContext(), "Error receiving WiFi broadcast ...", Toast.LENGTH_SHORT).show();
            }
        }

    };


    //Quando sair do app, parar o escaneamento
    @Override
    protected void onPause() {
        super.onPause();
        btnScan.setText("Start Scan");
        mHandler.removeCallbacks(mRunnable);
        timer.onFinish();

    }

}
