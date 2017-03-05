package cc.aoeiuv020.userdictionarybackup;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.provider.UserDictionary;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * bSave: 保存字典到私有文件，
 * bLoad: 从私有文件读取字典，
 * bExport: 导出备份文件到指定位置，
 * bImport: 导入指定文件到私有文件，
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BACKUP_TXT = "backup.txt";
    private Button bSave;
    private Button bLoad;
    private Button bExport;
    private Button bImport;
    private Button bEnable;
    private EditText ePath;
    private String[] projection = {
            UserDictionary.Words.LOCALE,
            UserDictionary.Words.SHORTCUT,
//            UserDictionary.Words.FREQUENCY,
//            UserDictionary.Words._ID,
            UserDictionary.Words.WORD,
//            UserDictionary.Words.APP_ID,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bSave = (Button) findViewById(R.id.iSave);
        bLoad = (Button) findViewById(R.id.iLoad);
        bExport = (Button) findViewById(R.id.iExport);
        bEnable = (Button) findViewById(R.id.iEnable);
        bImport = (Button) findViewById(R.id.iImport);
        ePath = (EditText) findViewById(R.id.iPath);
        ePath.setText(new File(Environment.getExternalStorageDirectory(), BACKUP_TXT)
                .getAbsolutePath());
        bSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserDictionary();
            }
        });
        bLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadUserDictionary();
            }
        });
        bExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportBackupFile();
            }
        });
        bImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importBackupFile();
            }
        });
        bEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableUserDictionaryIME();
            }
        });
    }

    private void importBackupFile() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                0);
        String sPath = ePath.getText().toString();
        try {
            InputStream input = new FileInputStream(sPath);
            OutputStream output = openFileOutput(BACKUP_TXT, Context.MODE_PRIVATE);
            byte[] buf = new byte[1024];
            int length;
            while ((length = input.read(buf)) > 0) {
                output.write(buf, 0, length);
            }
            input.close();
            output.close();
            Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "exportBackupFile: ", e);
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableUserDictionaryIME() {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        startActivity(intent);
    }

    private void exportBackupFile() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                0);
        File fExport = new File(ePath.getText().toString());
        //如果目标是目录，导出到目录下，
        if (fExport.exists() && fExport.isDirectory())
            fExport = new File(fExport, BACKUP_TXT);
        try (InputStream input = openFileInput(BACKUP_TXT); OutputStream output = new FileOutputStream(fExport)) {
            byte[] buf = new byte[1024];
            int length;
            while ((length = input.read(buf)) > 0) {
                output.write(buf, 0, length);
            }
            Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "exportBackupFile: ", e);
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveUserDictionary() {
        Cursor cursor = getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                projection, //返回这些列，
                null, null, //返回所有行，
                null //默认排序，
        );
        if (cursor == null) {
            error("查询失败");
            Toast.makeText(this, "查询失败", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray jArray = new JSONArray();
        JSONObject jObject;
        while (cursor.moveToNext()) {
            jObject = new JSONObject();
            for (int i = 0; i < projection.length; i++) {
                try {
                    jObject.put(projection[i], cursor.getString(i));
                } catch (JSONException e) {
                    //不可能到达，
                    error(e);
                    return;
                }
            }
            Log.d(TAG, "jObject: " + jObject.toString());
            jArray.put(jObject);
        }
        cursor.close();
        try (OutputStream output = openFileOutput(BACKUP_TXT, Context.MODE_PRIVATE)) {
            output.write(jArray.toString().getBytes());
        } catch (IOException e) {
            error(e);
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "保存成功 " + jArray.length(), Toast.LENGTH_SHORT).show();
    }

    private void error(String message) {
        Log.e(TAG, "error: ", new Exception(message));
    }

    private void error(Throwable e) {
        Log.e(TAG, "error: ", e);
    }

    private void loadUserDictionary() {
        Cursor cursor = getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                new String[]{UserDictionary.Words.WORD}, //只要word列，
                null, null, //返回所有行，
                null //默认排序，
        );
        if (cursor == null) {
            error("查询失败");
            Toast.makeText(this, "查询失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> wordSet = new HashSet<>();
        while (cursor.moveToNext()) {
            wordSet.add(cursor.getString(0));
        }
        cursor.close();
        JSONArray jArray;
        try (InputStreamReader reader = new InputStreamReader(openFileInput(BACKUP_TXT))) {
            char[] buf = new char[1024];
            int length;
            StringBuilder sb = new StringBuilder();
            while ((length = reader.read(buf)) > 0) {
                sb.append(buf, 0, length);
            }
            jArray = new JSONArray(sb.toString());
        } catch (IOException | JSONException e) {
            error(e);
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show();
            return;
        }
        int count = 0;
        for (int i = 0; i < jArray.length(); i++) {
            JSONObject jObject;
            try {
                jObject = jArray.getJSONObject(i);
                if (jObject.isNull(UserDictionary.Words.FREQUENCY))
                    jObject.put(UserDictionary.Words.FREQUENCY, 250);//默认词频250,
                Log.d(TAG, "loadUserDictionary: " + jObject.toString());
            } catch (JSONException e) {
                error(e);
                Toast.makeText(this, "备份文件错误", Toast.LENGTH_SHORT).show();
                return;
            }
            if (wordSet.contains(jObject.optString(UserDictionary.Words.WORD))) {
                continue;//已经有了就不继续插入了，
            }
            ContentValues word = new ContentValues(projection.length);
            for (String aProjection : projection) {
                //默认locale要为null,不能为空"",
                word.put(aProjection, jObject.optString(aProjection, null));
            }
            //有个UserDictionary.Words.addWord方法，
            //但是不方便，上面一个循环要展开，
            getContentResolver().insert(UserDictionary.Words.CONTENT_URI, word);
            count++;
        }
        Toast.makeText(this, String.format(Locale.CHINA, "读取成功 %d/%d", count, jArray.length()),
                Toast.LENGTH_SHORT)
                .show();
    }
}