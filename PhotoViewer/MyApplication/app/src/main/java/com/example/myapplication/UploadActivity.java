package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UploadActivity extends AppCompatActivity {

    EditText etTitle, etText;
    Button btnSelectImage, btnSubmitUpload, btnCancel;
    ImageView ivPreview;

    private Uri selectedImageUri = null;
    private Bitmap selectedBitmap = null;

    // 갤러리에서 이미지를 선택한 결과를 처리하는 런처
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    try {
                        // 선택한 이미지를 비트맵으로 변환하여 미리보기에 표시
                        selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                        ivPreview.setImageBitmap(selectedBitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        etTitle = findViewById(R.id.et_title);
        etText = findViewById(R.id.et_text);
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivPreview = findViewById(R.id.iv_preview);
        btnSubmitUpload = findViewById(R.id.btn_submit_upload);
        btnCancel = findViewById(R.id.btn_cancel);

        // "갤러리에서 이미지 선택" 버튼 클릭 리스너
        btnSelectImage.setOnClickListener(v -> openGallery());

        // "서버로 업로드" 버튼 클릭 리스너
        btnSubmitUpload.setOnClickListener(v -> uploadPost());

        btnCancel.setOnClickListener(v -> finish());
    }

    // 갤러리를 여는 메서드
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    // 업로드 버튼 클릭 시 실행되는 메서드
    private void uploadPost() {
        String title = etTitle.getText().toString();
        String text = etText.getText().toString();

        if (title.isEmpty() || text.isEmpty() || selectedBitmap == null) {
            Toast.makeText(this, "제목, 내용, 이미지를 모두 선택해야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // AsyncTask를 실행하여 서버로 전송
        new UploadPostTask().execute(title, text);
    }

    private class UploadPostTask extends AsyncTask<String, Void, String> {

        String boundary = "----" + System.currentTimeMillis(); // Multipart 경계 문자열
        String crlf = "\r\n";
        String twoHyphens = "--";

        @Override
        protected String doInBackground(String... params) {
            String title = params[0];
            String text = params[1];
            String token = "bf46b8f9337d1d27b4ef2511514c798be1a954b8";
            String site_url = "https://byeongmin.pythonanywhere.com/";
            String apiUrl = site_url + "/api_root/posts/";

            HttpURLConnection conn = null;
            DataOutputStream request = null;

            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Cache-Control", "no-cache");
                // Multipart 헤더 설정
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + this.boundary);

                request = new DataOutputStream(conn.getOutputStream());

                // --- 1. 'author' 파트 (Hardcoded '1') ---
                addFormField(request, "author", "1");

                // --- 2. 'title' 파트 ---
                addFormField(request, "title", title);

                // --- 3. 'text' 파트 ---
                addFormField(request, "text", text);

                // --- 4. 'image' 파트 (파일) ---
                addFilePart(request, "image", selectedBitmap);

                // --- 5. 'published_date' 파트 ---
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
                String isoDate = sdf.format(new Date());
                addFormField(request, "published_date", isoDate);

                // --- 요청 마무리 ---
                request.writeBytes(this.twoHyphens + this.boundary + this.twoHyphens + this.crlf);
                request.flush();
                request.close();

                // --- 서버 응답 받기 ---
                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK)
                        ? conn.getInputStream() : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                is.close();

                return "Response Code: " + responseCode + "\nResponse: " + response.toString();

            } catch (Exception e) {
                e.printStackTrace();
                return "Error: " + e.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        // AsyncTask 완료 후
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(UploadActivity.this, "업로드 결과:\n" + result, Toast.LENGTH_LONG).show();
            // 업로드 성공 시 (HTTP 201 Created) 화면을 닫고 메인으로 돌아감
            if (result.contains("Response Code: 201")) {
                // (선택사항) MainActivity가 새 목록을 자동 새로고침하게 할 수 있음
                finish();
            }
        }

        // Multipart 폼 데이터 (텍스트) 추가 헬퍼 메서드
        private void addFormField(DataOutputStream request, String name, String value) throws IOException {
            request.writeBytes(this.twoHyphens + this.boundary + this.crlf);
            request.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + this.crlf);
            request.writeBytes(this.crlf);
            request.write(value.getBytes("UTF-8")); // UTF-8로 인코딩
            request.writeBytes(this.crlf);
        }

        // Multipart 폼 데이터 (이미지 파일) 추가 헬퍼 메서드
        private void addFilePart(DataOutputStream request, String fieldName, Bitmap bitmap) throws IOException {
            // 비트맵을 JPEG 바이트 배열로 변환
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
            byte[] fileData = stream.toByteArray();

            request.writeBytes(this.twoHyphens + this.boundary + this.crlf);
            request.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\";filename=\"upload.jpg\"" + this.crlf);
            request.writeBytes("Content-Type: image/jpeg" + this.crlf);
            request.writeBytes(this.crlf);
            request.write(fileData); // 이미지 바이트 데이터 쓰기
            request.writeBytes(this.crlf);
        }
    }
}