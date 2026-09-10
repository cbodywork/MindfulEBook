package kr.co.mindfulebook;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.media.*;
import android.net.Uri;
import android.speech.tts.*;
import android.util.*;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

public class MainActivity extends Activity {
    private WebView web; private TextToSpeech tts; private boolean voiceReady=false; private volatile boolean closed=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private File books; private AudioManager audio; private AudioFocusRequest focus;
    private String voiceMessage="한국어 음성 확인 중…";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); books=new File(getFilesDir(),"books");books.mkdirs();PDFBoxResourceLoader.init(getApplicationContext());
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change<0)pause();},main).build();
        web=new WebView(this);web.setBackgroundColor(Color.rgb(247,247,242));setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setDefaultTextEncodingName("UTF-8");settings.setTextZoom(100);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;} @Override public void onPageFinished(WebView v,String u){emit("voice",voiceMessage);}});
        web.addJavascriptInterface(new Bridge(),"Reader");web.loadUrl("file:///android_asset/index.html");
        tts=new TextToSpeech(this,status->{
            if(closed)return;
            if(status==TextToSpeech.SUCCESS){
                Set<Voice> voices=tts.getVoices();Voice chosen=null;
                if(voices!=null)for(Voice voice:voices){if(voice.getLocale().getLanguage().equals("ko")&&!voice.isNetworkConnectionRequired()&&!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)){if(chosen==null||voice.getQuality()>chosen.getQuality())chosen=voice;}}
                if(chosen!=null){tts.setVoice(chosen);voiceReady=true;voiceMessage="한국어 오프라인 음성 준비됨";}
                else voiceMessage="한국어 오프라인 음성을 설치한 뒤 앱을 다시 열어주십시오.";
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){emit("spoken",id);}public void onError(String id){emit("speechError","낭독하지 못했습니다. 음성 설정을 확인하십시오.");}});
            }else voiceMessage="음성 엔진이 없습니다. 기기의 TTS 설정을 확인하십시오.";
            emit("voice",voiceMessage);
        });
    }
    void emit(String event,String data){main.post(()->{if(!closed)web.evaluateJavascript("window.nativeEvent && window.nativeEvent("+JSONObject.quote(event)+","+JSONObject.quote(data)+")",null);});}
    void pause(){if(tts!=null)tts.stop();audio.abandonAudioFocusRequest(focus);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);emit("paused","");}
    @Override protected void onPause(){pause();super.onPause();}
    @Override protected void onDestroy(){closed=true;if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();web.removeJavascriptInterface("Reader");web.destroy();super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);web.requestApplyInsets();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.back && window.back()",null);}
    String readFile(File f)throws Exception{try(InputStream in=new FileInputStream(f)){return new String(BookParser.read(in,40_000_000),java.nio.charset.StandardCharsets.UTF_8);}}
    void writeFile(File f,String data)throws Exception{AtomicFile atom=new AtomicFile(f);FileOutputStream out=null;try{out=atom.startWrite();out.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));atom.finishWrite(out);}catch(Exception e){if(out!=null)atom.failWrite(out);throw e;}}
    File bookFile(String id,String ext)throws Exception{if(!id.matches("[a-f0-9-]{36}"))throw new IOException("책 식별자가 올바르지 않습니다.");return new File(books,id+ext);}
    String catalog(){JSONArray a=new JSONArray();File[] files=books.listFiles((d,n)->n.endsWith(".meta"));if(files!=null){Arrays.sort(files,(x,y)->Long.compare(y.lastModified(),x.lastModified()));for(File f:files)try{a.put(new JSONObject(readFile(f)));}catch(Exception ignored){}}return a.toString();}
    class Bridge {
        @JavascriptInterface public String state(){return getPreferences(MODE_PRIVATE).getString("state","{}");}
        @JavascriptInterface public void save(String state){if(state.length()<1_000_000)getPreferences(MODE_PRIVATE).edit().putString("state",state).commit();}
        @JavascriptInterface public String catalog(){return MainActivity.this.catalog();}
        @JavascriptInterface public void open(String id){worker.execute(()->{try{emit("book",readFile(bookFile(id,".json")));}catch(Exception e){emit("error","책을 열 수 없습니다: "+e.getMessage());}});}
        @JavascriptInterface public void remove(String id){worker.execute(()->{try{for(String ext:new String[]{".json",".meta",".source"}){File f=bookFile(id,ext);if(f.exists()&&!f.delete())throw new IOException("파일 삭제 실패");}emit("catalog",catalog());}catch(Exception e){emit("error",e.getMessage());}});}
        @JavascriptInterface public void pick(){main.post(()->{pause();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){emit("error","기기에 파일 선택 앱이 없습니다.");}});}
        @JavascriptInterface public void stop(){main.post(()->{if(tts!=null)tts.stop();audio.abandonAudioFocusRequest(focus);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});}
        @JavascriptInterface public void say(String text,String id,float rate,String dictionary){main.post(()->{
            if(!voiceReady){emit("speechError",voiceMessage);return;}
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){emit("speechError","다른 앱이 소리를 사용 중입니다. 잠시 뒤 다시 시도하십시오.");return;}
            String spoken=TextTools.pronounce(text,dictionary);
            if(spoken.length()>TextToSpeech.getMaxSpeechInputLength()){emit("speechError","발음사전 변환 결과가 너무 깁니다. 항목을 짧게 수정하십시오.");return;}
            tts.setSpeechRate(Math.max(.5f,Math.min(1.8f,rate)));getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            int result=tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,id);if(result==TextToSpeech.ERROR)emit("speechError","음성 재생 요청에 실패했습니다.");
        });}
        @JavascriptInterface public void speechSettings(){main.post(()->{pause();try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}});}
        @JavascriptInterface public void pdf(String id,int number){worker.execute(()->{
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(bookFile(id,".source"),ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)){
                int page=Math.max(0,Math.min(number,pdf.getPageCount()-1));
                try(PdfRenderer.Page p=pdf.openPage(page)){
                    float ratio=Math.min(1600f/p.getWidth(),2200f/p.getHeight());int w=Math.max(1,(int)(p.getWidth()*ratio)),h=Math.max(1,(int)(p.getHeight()*ratio));
                    Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();
                    emit("pdf",new JSONObject().put("id",id).put("page",page).put("image","data:image/png;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)).toString());
                }
            }catch(Exception e){emit("error","PDF 원본을 표시할 수 없습니다: "+e.getMessage());}
        });}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=42||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();emit("busy","문서를 기기에 저장하고 본문을 읽는 중입니다…");
        worker.execute(()->{
            String id=UUID.randomUUID().toString();File source=new File(books,id+".source");
            try{
                String name="전자책";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                String type=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
                if(!Arrays.asList("txt","epub","docx","pdf").contains(type))throw new IOException("TXT·EPUB·PDF·DOCX 파일만 지원합니다.");
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(source)){
                    if(in==null)throw new IOException("파일을 읽을 수 없습니다.");byte[] buf=new byte[16384];long count=0;int n;
                    while((n=in.read(buf))!=-1){count+=n;if(count>50_000_000)throw new IOException("50MB 이하의 파일을 선택하십시오.");out.write(buf,0,n);}
                }
                JSONObject book=BookParser.parse(source,name,type);book.put("id",id);
                writeFile(new File(books,id+".json"),book.toString());
                writeFile(new File(books,id+".meta"),new JSONObject().put("id",id).put("title",book.getString("title")).put("type",type).put("count",book.getJSONArray("paragraphs").length()).toString());
                emit("catalog",catalog());emit("book",book.toString());
            }catch(Exception e){source.delete();new File(books,id+".json").delete();new File(books,id+".meta").delete();emit("error","가져오기 실패: "+e.getMessage());}
        });
    }
}
