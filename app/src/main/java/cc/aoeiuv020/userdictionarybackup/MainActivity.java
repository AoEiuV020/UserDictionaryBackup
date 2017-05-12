package cc.aoeiuv020.userdictionarybackup;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.provider.UserDictionary;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * bSave: 保存字典到私有文件，
 * bLoad: 从私有文件读取字典，
 * bExport: 导出备份文件到指定位置，
 * bImport: 导入指定文件到私有文件，
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String BACKUP_TXT = "backup.txt";
    private EditText ePath;
    private Gson gson = new GsonBuilder()
//            .setPrettyPrinting()
            .create();
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
        ePath = (EditText) findViewById(R.id.iPath);
        ePath.setText(new File(Environment.getExternalStorageDirectory(), BACKUP_TXT)
                .getAbsolutePath());
    }

    public void importBackupFile(View v) {
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

    public void enableUserDictionaryIME(View v) {
        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
        startActivity(intent);
    }

    public void exportBackupFile(View v) {
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

    public void saveUserDictionary(View v) {
        Cursor cursor = getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                projection, //返回这些列，
                null, null, //返回所有行，
                null //默认排序，
        );
        if (cursor == null) {
            error("查询失败");
            return;
        }
        Backup backup = new Backup();
        while (cursor.moveToNext()) {
            Words words = new Words();
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                words.add(cursor.getString(i));
            }
            Log.d(TAG, "words: " + words.toString());
            backup.add(words);
        }
        cursor.close();
        try (OutputStream output = openFileOutput(BACKUP_TXT, Context.MODE_PRIVATE)) {
            output.write(gson.toJson(backup).getBytes());
        } catch (IOException e) {
            error("保存失败", e);
            return;
        }
        Toast.makeText(this, "保存成功 " + backup.size(), Toast.LENGTH_SHORT).show();
    }

    private void error(String message) {
        error(message, new Exception());
    }

    private void error(String message, Throwable e) {
        Log.e(TAG, message, e);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void loadUserDictionary(View v) {
        Cursor cursor = getContentResolver().query(UserDictionary.Words.CONTENT_URI,
                new String[]{UserDictionary.Words.WORD}, //只要word列，
                null, null, //返回所有行，
                null //默认排序，
        );
        if (cursor == null) {
            error("查询失败");
            return;
        }
        Set<String> wordSet = new HashSet<>();
        while (cursor.moveToNext()) {
            wordSet.add(cursor.getString(0));
        }
        cursor.close();
        Backup backup;
        try (InputStreamReader reader = new InputStreamReader(openFileInput(BACKUP_TXT))) {
            char[] buf = new char[1024];
            int length;
            StringBuilder sb = new StringBuilder();
            while ((length = reader.read(buf)) > 0) {
                sb.append(buf, 0, length);
            }
            backup = gson.fromJson(sb.toString(), Backup.class);
        } catch (IOException e) {
            error("读取失败", e);
            return;
        }
        int count = 0;
        for (Words words : backup) {
            Log.d(TAG, "loadUserDictionary: " + words.toString());
            ContentValues contentValues;
            try {
                contentValues = new ContentValues(projection.length);
                for (int i = 0; i < projection.length; i++) {
                    //默认locale要为null,不能为空"",
                    contentValues.put(projection[i], words.get(i));
                }
            } catch (ClassCastException e) {
                error("备份文件格式错误", e);
                return;
            }
            if (wordSet.contains(contentValues.getAsString(UserDictionary.Words.WORD))) {
                continue;//已经有了就不继续插入了，
            }
            //词频统一250,这也是安卓手动添加时默认的，
            contentValues.put(UserDictionary.Words.FREQUENCY, 250);
            //有个UserDictionary.Words.addWord方法，
            //但是不方便，上面一个循环要展开，
            getContentResolver().insert(UserDictionary.Words.CONTENT_URI, contentValues);
            count++;
        }
        Toast.makeText(this, String.format(Locale.CHINA, "读取成功 %d/%d", count, backup.size()),
                Toast.LENGTH_SHORT)
                .show();
    }

    @SuppressWarnings("WeakerAccess")
    public static class Words extends ArrayList<String> {
    }

    @SuppressWarnings("WeakerAccess")
    public static class Backup extends ArrayList<Words> {
    }
}