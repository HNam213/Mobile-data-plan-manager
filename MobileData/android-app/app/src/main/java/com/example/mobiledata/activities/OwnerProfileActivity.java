package com.example.mobiledata.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.CustomerProfileRules;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.SessionManager;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Ho so chu so dien thoai; luu tren SQL Server thong qua API (khong phai popup). */
public class OwnerProfileActivity extends AppCompatActivity {
    private static final int PICK_IMAGE = 7041;
    private static final int INK = Color.rgb(21, 51, 92);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private ImageView avatar;
    private EditText fullName, birthYear, hometown, address, email;
    private Spinner gender;
    private TextView identity;
    private Button save;
    private String token;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = new SessionManager(this).getAccessToken();
        if (token.isEmpty()) { finish(); return; }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 246, 251));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(page);
        setContentView(scroll);

        TextView title = text("Hồ sơ chủ thuê bao", 24, true);
        page.addView(title);
        TextView hint = text("Thông tin cá nhân và liên hệ", 13, false);
        hint.setPadding(0, dp(7), 0, dp(20)); page.addView(hint);
        avatar = new ImageView(this);
        avatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(Color.rgb(223, 235, 255)); avatarBg.setCornerRadius(dp(64));
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(116),dp(116));
        avlp.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(avatar,avlp);
        Button changePhoto = button("Đổi ảnh đại diện", false);
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(-1,dp(48));
        photoLp.setMargins(0,dp(12),0,dp(22));
        page.addView(changePhoto,photoLp);
        changePhoto.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(pick,"Chọn ảnh đại diện"), PICK_IMAGE);
        });
        identity = text("Đang tải thông tin thuê bao...",14,false);
        identity.setPadding(dp(12),dp(14),dp(12),dp(14));
        identity.setBackground(rounded(Color.WHITE));
        page.addView(identity);
        fullName = field(page,"Họ và tên",false);
        TextView genderTitle = requiredTitle("Giới tính");
        LinearLayout.LayoutParams genderLp = new LinearLayout.LayoutParams(-1,-2);
        genderLp.topMargin = dp(14); page.addView(genderTitle, genderLp);
        gender = new Spinner(this);
        String[] choices = {"Chọn giới tính", "Nam", "Nữ", "Khác"};
        gender.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, choices));
        page.addView(gender, new LinearLayout.LayoutParams(-1, dp(48)));
        CustomerProfileRules.attachGender(page, gender, null);
        birthYear = field(page,"Năm sinh",true);
        hometown = field(page,"Quê quán",false);
        address = field(page,"Địa chỉ",false);
        email = field(page,"Email",false);
        save = button("Lưu thông tin hồ sơ",true);
        LinearLayout.LayoutParams sv = new LinearLayout.LayoutParams(-1,dp(52));
        sv.topMargin=dp(20);
        page.addView(save,sv);
        save.setOnClickListener(v -> saveProfile());
        loadProfile();
    }

    private void loadProfile() {
        MobileDataBusinessClient.get("/api/me/profile",token,new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject o) {
                if (isFinishing() || isDestroyed()) return;
                identity.setText("CCCD: "+o.optString("cccd")+"\nSố điện thoại: "+
                        o.optString("phone")+"\nNhà mạng: "+o.optString("carrierName"));
                fullName.setText(o.optString("fullName"));
                String selected = o.optString("gender");
                String[] values = {"", "Nam", "Nữ", "Khác"};
                for (int i=0;i<values.length;i++) if (values[i].equals(selected)) {
                    gender.setSelection(i); break;
                }
                birthYear.setText(o.isNull("birthYear") ? "" : o.optString("birthYear"));
                hometown.setText(o.optString("hometown"));
                address.setText(o.optString("address"));
                email.setText(o.optString("email"));
                String photo=o.optString("photoBase64","");
                if(!photo.isEmpty()) try {
                    byte[] data=Base64.decode(photo,Base64.DEFAULT);
                    Bitmap bmp=BitmapFactory.decodeByteArray(data,0,data.length);
                    if(bmp!=null) avatar.setImageBitmap(bmp);
                } catch(Exception ignored) { }
            }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void saveProfile() {
        if (!CustomerProfileRules.validateAll(null, fullName, gender,
                birthYear, hometown, address, email)) return;
        save.setEnabled(false);
        JSONObject o=new JSONObject();
        try {
            o.put("fullName", fullName.getText().toString().trim());
            o.put("gender", gender.getSelectedItemPosition() == 0 ? ""
                    : gender.getSelectedItem().toString());
            String year=birthYear.getText().toString().trim();
            if(year.isEmpty())o.put("birthYear",JSONObject.NULL);
            else o.put("birthYear",Integer.parseInt(year));
            o.put("hometown",hometown.getText().toString().trim());
            o.put("address",address.getText().toString().trim());
            o.put("email",email.getText().toString().trim());
        } catch(Exception ex) {save.setEnabled(true);toast("Năm sinh phải là số hợp lệ.");return;}
        MobileDataBusinessClient.put("/api/me/profile",o,token,new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {save.setEnabled(true);toast(result.optString("message","Đã lưu hồ sơ."));}
            @Override public void onError(String err) {save.setEnabled(true);toast(err);}
        });
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IMAGE || resultCode!=Activity.RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
            int sample=1;
            while(options.outWidth/sample>512 || options.outHeight/sample>512)sample*=2;
            options.inJustDecodeBounds=false; options.inSampleSize=sample;
            Bitmap decoded;
            try(InputStream in=getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,options);}
            if(decoded==null){toast("Không đọc được ảnh.");return;}
            float ratio=Math.min(1f, 320f/Math.max(decoded.getWidth(),decoded.getHeight()));
            Bitmap scaled=Bitmap.createScaledBitmap(decoded,
                    Math.max(1,Math.round(decoded.getWidth()*ratio)),
                    Math.max(1,Math.round(decoded.getHeight()*ratio)),true);
            if(scaled!=decoded)decoded.recycle();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG,75,out);
            byte[] bytes=out.toByteArray();
            if(bytes.length>256*1024){toast("Ảnh vượt quá giới hạn 256KB.");return;}
            JSONObject body=new JSONObject(); body.put("photoBase64", Base64.encodeToString(bytes,Base64.NO_WRAP));
            MobileDataBusinessClient.post("/api/me/profile/photo",body,token,new MobileDataBusinessClient.Callback() {
                @Override public void onSuccess(JSONObject r){avatar.setImageBitmap(scaled);toast("Đã đổi ảnh đại diện.");}
                @Override public void onError(String e){toast(e);}
            });
        } catch(Exception ex){toast("Không thể mở ảnh đã chọn.");}
    }

    private EditText field(LinearLayout parent,String label,boolean numeric){
        TextView name=requiredTitle(label);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);
        parent.addView(name,lp);
        EditText input=new EditText(this);
        input.setSingleLine(true);input.setTextSize(16);input.setTextColor(INK);
        input.setHintTextColor(Color.GRAY);input.setBackground(rounded(Color.WHITE));
        input.setPadding(dp(14),dp(7),dp(14),dp(7));
        if (label.equals("Năm sinh")) MobileDataInputRules.digits(input, 4);
        else if (label.equals("Họ và tên")) MobileDataInputRules.text(input, 100);
        else if (label.equals("Quê quán")) MobileDataInputRules.text(input, 120);
        else if (label.equals("Địa chỉ")) MobileDataInputRules.text(input, 200);
        else if (label.equals("Email")) MobileDataInputRules.email(input);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(48));ep.topMargin=dp(6);
        parent.addView(input,ep);
        CustomerProfileRules.attach(parent, input, null);
        return input;
    }
    private TextView requiredTitle(String label) {
        TextView result = text(label + "  ●", 14, true);
        android.text.SpannableString styled = new android.text.SpannableString(label + "  ●");
        styled.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(204,45,61)),
                styled.length()-1, styled.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setText(styled);
        return result;
    }
    private GradientDrawable rounded(int fill){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(12));return g;}
    private Button button(String value,boolean primary){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:BLUE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?BLUE:Color.rgb(226,237,255)));return b;}
    private TextView text(String value,int sp,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(INK);t.setTextSize(sp);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
}
