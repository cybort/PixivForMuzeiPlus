package moe.democyann.pixivformuzeiplus;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Exchanger;

import moe.democyann.pixivformuzeiplus.util.Cookie;
import moe.democyann.pixivformuzeiplus.util.Pixiv;
import moe.democyann.pixivformuzeiplus.util.TagFliter;

public class PixivSource extends RemoteMuzeiArtSource{
    private static final String TAG = "PixivSource";
    private static final String SOURCE_NAME = "PixivSource";
    private static Pixiv pixiv=new Pixiv();;
    private static String prevfile;
    private final  int MINUTE=60*1000;
    private static String token="";
    private static List list=null;
    private static long last=0;
    private static JSONArray rall=null;
    private static boolean loadflag=false;

    private static String pixivid="";
    private static String password="";


    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            loadflag=true;

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
                loadflag=false;
                return;
            }

            while(true) {
                List<Artwork> loadlist = getArtlist();
                Log.i(TAG, "run: SIZE"+loadlist.size());
                if(loadlist.size()>=5) break;
                try {
                    Artwork a=pixivUserPush();
                    if(a!=null) {
                        loadlist=getArtlist();
                        loadlist.add(a);
                    }
                } catch (RetryException e) {
                    Log.e(TAG, "run: get img", e);
                    loadflag=false;
                    return;
                }
                setArtlist(loadlist);
            }
            loadflag=false;
        }
    };

    private Thread t1 = new Thread(runnable);

    public PixivSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }

    @Override
    public void onTryUpdate(int reason) throws RetryException {
        Log.i(TAG, "onTryUpdate: "+reason);

        if (isOnlyUpdateOnWifi() && !isEnabledWifi()) {
            Log.d(TAG, "no wifi");
            scheduleUpdate();
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean defaultValue = false,
                v = preferences.getBoolean("is_user", defaultValue);
        Log.i(TAG, "onTryUpdate: "+v);
        Artwork artwork=getCurrentArtwork();
        Uri temp;
        String temptoken;
        if(getCurrentArtwork()!=null)
        {
            temptoken=artwork.getToken();
            temp=artwork.getImageUri();
        }else{
            temp=Uri.parse("");
            temptoken=null;

        }

        Artwork a1= new Artwork.Builder()
                .title(getResources().getString(R.string.loading))
                .byline(getResources().getString(R.string.loading))
                .imageUri(temp)
                .token(temptoken)
                .build();
        publishArtwork(a1);
        scheduleUpdate(0);

        if(pixiv.getCookie()==null || "".equals(pixiv.getCookie().toString())) {
            String cookiestr = getLocalCookie();
            if (!"".equals(cookiestr)) {
                String[] cookarr = cookiestr.split(";");
                Cookie c = new Cookie();
                for (String cr : cookarr) {
                    c.add(cr);
                }
                pixiv.setCookie(c);
                flushToken();
            }
        }

        try{
            if (v) {
                pixivid = getUserName();
                password = getPassword();
                List<Artwork> loadlist = getArtlist();
                if (!"".equals(pixivid) && !"".equals(password)) {
                    if(loadlist.size()>0) {
                        for(Artwork aaa:loadlist) {
                            Log.i(TAG, "onTryUpdate: loadlist" + aaa.getTitle()+aaa.getToken());
                        }
                        artwork=loadlist.get(0);
                        loadlist.remove(0);
                        setArtlist(loadlist);
                    }else{
                        artwork = pixivUserPush();
                    }
                    Log.i(TAG, "onTryUpdate: loadflag"+loadflag);
                    if(!loadflag) {
                        loadflag=true;
                        Log.i(TAG, "onTryUpdate: Thread RUN *****************************");
                        t1=new Thread(runnable);
                        t1.start();
                    }

                } else {
                    Log.i(TAG, "onTryUpdate: 还未设置PixivID及密码");
                    artwork =noPixivUser(getResources().getString(R.string.u_err));
                }
            } else {
                artwork=noPixivUser("");
            }
        }catch (Exception e){
            Log.e(TAG, "onTryUpdate: ",e );
            publishArtwork(getCurrentArtwork());
        }finally {
            if(artwork!=null) {
                publishArtwork(artwork);
                scheduleUpdate(5000);
                try {
                    if (prevfile != null && !"".equals(prevfile)) {
                        Application app = getApplication();
                        final File prev = new File(app.getExternalCacheDir(), prevfile);
                        prev.delete();
                    }
                }catch (Exception e){
                    scheduleUpdate(5000);
                    e.printStackTrace();
                }

                prevfile=artwork.getToken();

            }else{
                scheduleUpdate(5000);
                return;
            }
            setLocalSet("cookie",pixiv.getCookie());
            scheduleUpdate();
        }
    }
    private boolean isOnlyUpdateOnWifi() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean defaultValue = false,
                v = preferences.getBoolean("only_wifi", defaultValue);
        Log.d(TAG, "pref_onlyWifi = " + v);
        return v;
    }
    private boolean isEnabledWifi() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi.isConnected();
    }

    private String getUserName(){
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(this);
        String defaultValue = "",
                v = preferences.getString("pixivid", defaultValue);
        return v;
    }
    private String getPassword(){
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(this);
        String defaultValue = "",
                v = preferences.getString("password", defaultValue);
        return v;
    }

    private String getLocalCookie(){
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(this);
        String defaultValue = "",
                v = preferences.getString("cookie", defaultValue);
        Log.i(TAG, "getLocalCookie: "+v);
        return v;
    }
    private void setLocalSet(String key,String value){
        Log.i(TAG, "setLocalCookie: ");
        SharedPreferences preferences=PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor= preferences.edit();
        editor.putString(key,value);
        editor.apply();
    }
    public Artwork noPixivUser(String mser)throws RetryException{
        if(rall==null || rall.length()==0 || (System.currentTimeMillis()-last)>(120*MINUTE)){
            rall=pixiv.getRalllist();
            last=System.currentTimeMillis();
//            setLocalSet("last",String.valueOf(last));
        }

        if(rall!=null && rall.length()>0){
            JSONObject o;
            String user_id = "1";
            String img_id = "1";
            String img_url = "";
            String user_name;
            String illust_title;
            String tags = "";
            while(true) {
                Random r = new Random();
                int i = r.nextInt(rall.length());
                try {
                    o = rall.getJSONObject(i);
                    user_id = o.getString("user_id");
                    img_id = o.getString("illust_id");
                    img_url = o.getString("url");
                    user_name = o.getString("user_name");
                    illust_title = o.getString("title");
                    tags = o.getString("tags");
                } catch (JSONException e) {
                    Log.e(TAG, e.toString(), e);
                    throw new RetryException();
                }

                Log.i(TAG, tags);
                if (getIs_no_R18()) {
                    if (TagFliter.is_r18(tags)) continue;
                }

                if (getIs_check_Tag()) {
                    if (!TagFliter.checkTagAll(getTags(), tags)) break;
                } else {
                    break;
                }
            }

            Application app = getApplication();
            if(app==null){
                Log.e(TAG, "onTryUpdate: APP Error" );
                throw new RetryException();
            }

            File file = new File(app.getExternalCacheDir(),user_id+img_id);
            Uri fileUri =pixiv.downloadImage(img_url,img_id,file);
            if(!mser.equals("")) user_name=mser;
            Artwork artwork = new Artwork.Builder()
                    .title(illust_title)
                    .byline(user_name)
                    .imageUri(fileUri)
                    .token(user_id+img_id)
                    .viewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + img_id)))
                    .build();
//            publishArtwork(artwork);
//            if (prevfile != null) {
//                final File prev = new File(app.getExternalCacheDir(),prevfile);
//                prev.delete();
//            }else{
//                prevfile=user_id+img_id;
//            }
//            scheduleUpdate();
            return artwork;
        }
        return null;
    }



    private Artwork pixivUserPush() throws RetryException {
        if(token.equals("")) {
            token=pixiv.getToken(pixivid, password,true);
            if("".equals(token)){
                Artwork a = new Artwork.Builder()
                        .imageUri(getCurrentArtwork().getImageUri())
                        .title(getCurrentArtwork().getTitle())
                        .token(getCurrentArtwork().getToken())
                        .byline(getResources().getString(R.string.login_failed))
                        .build();

                prevfile="";
                return a;
            }
        }

        if(!token.equals("")){
            if(list==null|| list.size()==0 || (System.currentTimeMillis()-last)>(120*MINUTE)) {
                list = pixiv.getRcomm();
                last=System.currentTimeMillis();
//                setLocalSet("last",String.valueOf(last));
                Log.i(TAG, "List Update");
                Log.i(TAG, "List Size:"+list.size());
                Log.i(TAG, "time:"+(System.currentTimeMillis()-last));
            }
            if(list!=null && list.size()!=0){
                JSONObject data;
                JSONObject illust;
                JSONObject r18obj=null;
                String img_url="";
                boolean r18_flag=false;

                while(true) {
                    Random random = new Random();
                    int i = random.nextInt(list.size());
                    Log.i(TAG, "pixivUserPush: GET I ======= "+i);
                    String imgid = String.valueOf(list.get(i));

                    data = pixiv.getIllInfo(imgid);
                    if (data == null) {
                        flushToken();
                        return null;
                    }
                    long views;

                    try{
                        illust=data.getJSONObject("illust");
                        views=illust.getLong("total_view");
                    } catch (JSONException e) {
                        Log.e(TAG, e.toString(),e );
                        throw new RetryException();
                    }
                    Log.i(TAG, "pixivUserPush: Views = "+views);
                    if(views<getViews()){
                        Log.i(TAG, "浏览数不足，重新加载"+getViews());
                        continue;
                    }
                    JSONObject imgurls;
                    try{
                        if(illust.getInt("page_count")>1){
                            imgurls=illust.getJSONArray("meta_pages").getJSONObject(0).getJSONObject("image_urls");
                            img_url=imgurls.getString("original");
                        }else {
                            imgurls = illust.getJSONObject("meta_single_page");
                            img_url=imgurls.getString("original_image_url");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, e.toString(),e );
                        throw new RetryException();
                    }

                    CharSequence c= "limit_r18";
                    if(img_url.contains(c)){
                        if(getIs_no_R18()){continue;}
                        else{
                            r18_flag=true;
                            r18obj=pixiv.getIllInfo2(imgid);
                        }
                    }

                    String tags="";
                    try {
                        if(r18_flag) {
                            tags=r18obj.getString("tags");
                        }else {
                            tags = illust.getString("tags");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.toString(), e);
                        throw new RetryException();
                    }

                    Log.i(TAG, tags);
//                    if(getIs_no_R18()){
//                        if(TagFliter.is_r18(tags)) continue;
//                    }

                    if(getIs_check_Tag()){
                        if(!TagFliter.checkTagAll(getTags(),tags)) break;
                    }else{
                        break;
                    }

                }
                Application app = getApplication();
                if(app==null){
                    Log.e(TAG, "onTryUpdate: APP Error" );
                    throw new RetryException();
                }
                String user_id="1";
                String img_id="1";

                String user_name;
                String illust_title;

                JSONObject user;
//                String tag;
                try {
                    if(r18_flag){
                        user_id=r18obj.getString("illust_user_id");
                        img_id=r18obj.getString("illust_id");
                        img_url=r18obj.getString("url");
                        user_name=r18obj.getString("user_name");
                        illust_title=r18obj.getString("illust_title");
                    } else {
                        user = illust.getJSONObject("user");

                        user_id = user.getString("id");
                        img_id = illust.getString("id");

                        user_name = user.getString("name");
                        illust_title = illust.getString("title");
                    }
//
//                    tag=o.getString("tags");
                } catch (JSONException e) {
                    Log.e(TAG, e.toString(),e );
                    throw new RetryException();
                }

                Random r = new Random();
                int nr = r.nextInt(1000);
                File file = new File(app.getExternalCacheDir(),user_id+img_id+nr);
                Uri fileUri;
                if(r18_flag){
                    fileUri= pixiv.downloadImage(img_url,img_id,file);
                }
                else {
                    fileUri = pixiv.downloadImage2(img_url, img_id, file);
                }
                Artwork artwork = new Artwork.Builder()
                        .title(illust_title)
                        .byline(user_name)
                        .imageUri(fileUri)
                        .token(user_id+img_id+nr)
                        .viewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + img_id)))
                        .build();
                return artwork;
//                publishArtwork(artwork);
//                if (prevfile != null) {
//                    final File prev = new File(app.getExternalCacheDir(),prevfile);
//                    prev.delete();
//                }else{
//                    prevfile=user_id+img_id;
//                }
//                scheduleUpdate();
            }
        }
        return null;
    }


    private void flushToken() throws RetryException {
        Log.i(TAG, "flushToken: ");
        token="";
        token=pixiv.getToken(null,null,false);
        if(token.equals("")){
            token=pixiv.getToken(pixivid,password,true);
        }
        scheduleUpdate(5000);
    }

    private int getChangeInterval() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String defaultValue = getString(R.string.time_default),
                s = preferences.getString("time_change", defaultValue);
        Log.d(TAG, "time_change = \"" + s + "\"");
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            Log.w(TAG, e.toString(), e);
            return 0;
        }
    }

    private void scheduleUpdate() {
        int changeInterval = getChangeInterval();
        if (changeInterval > 0) {
            scheduleUpdate(System.currentTimeMillis() + changeInterval * MINUTE);
        }
    }

    private boolean getIs_no_R18(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean defaultValue = true,
                v = preferences.getBoolean("is_no_r18", defaultValue);
        return v;
    }
    private long getViews(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultValue = "0",
                s = preferences.getString("views", defaultValue);
        long v=0;
        try{
            v=Long.valueOf(s);
        }catch (Exception e){
            Log.e(TAG, "getViews: ", e);
        }
        if(v>50000) v=50000;
        return v;
    }

    private String getTags(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultValue = "",
                v = preferences.getString("tags", defaultValue);
        return v;
    }

    private boolean getIs_check_Tag(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean defaultValue = false,
                v = preferences.getBoolean("is_tag", defaultValue);
        return v;
    }

    private List<Artwork> getArtlist(){
        List<Artwork> loadlist= new ArrayList<Artwork>();
        SharedPreferences preferences =PreferenceManager.getDefaultSharedPreferences(PixivSource.this);
        String defaultValue="",
                v=preferences.getString("load",defaultValue);
//        Log.i(TAG, "getArtlist: "+v);
        JSONArray array;
        if(!"".equals(v)) {
            try {
                array = new JSONArray(v);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, "run: to arr", e);
                return loadlist;
            }
        }else{
            array=new JSONArray();
        }

        for(int i=0;i<array.length();i++) {
            try {
                JSONObject o= array.getJSONObject(i);
                if(o!=null) {
                    loadlist.add(Artwork.fromJson(o));
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e(TAG, "run: to obj", e);
                return loadlist;
            }
        }
        return loadlist;
    }

    private void setArtlist(List<Artwork> loadlist){
        JSONArray array;
        array= new JSONArray();
        for(Artwork ar:loadlist){
            try {
                array.put(ar.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        SharedPreferences preferences =PreferenceManager.getDefaultSharedPreferences(PixivSource.this);
        SharedPreferences.Editor editor = preferences.edit();
//        Log.i(TAG, "setArtlist: "+array.toString());
        editor.putString("load",array.toString());
        editor.apply();
    }

}
