package kr.co.mindfulebook;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.content.*;
import android.webkit.WebView;
import android.view.ViewGroup;
import java.io.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
@RunWith(AndroidJUnit4.class)
public class ReaderDeviceTest {
 File file(String ext)throws Exception{return File.createTempFile("testbook",ext,InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());}
 void zip(File f,String... entries)throws Exception{try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(f))){for(int i=0;i<entries.length;i+=2){out.putNextEntry(new ZipEntry(entries[i]));out.write(entries[i+1].getBytes(StandardCharsets.UTF_8));out.closeEntry();}}}
 @Test public void importTxt()throws Exception{File f=file(".txt");try(FileOutputStream out=new FileOutputStream(f)){out.write("제1장. 알아차림\n한국어 본문입니다.".getBytes("MS949"));}JSONObject b=BookParser.parse(f,"한글.txt","txt");assertEquals("한국어 본문입니다.",b.getJSONArray("paragraphs").getString(1));assertEquals(1,b.getJSONArray("chapters").length());f.delete();}
 @Test public void importEpubSpine()throws Exception{File f=file(".epub");zip(f,"META-INF/container.xml","<container><rootfiles><rootfile full-path=\"OPS/package.opf\"/></rootfiles></container>","OPS/package.opf","<package><manifest><item id=\"a\" href=\"a.xhtml\"/><item id=\"b\" href=\"b.xhtml\"/></manifest><spine><itemref idref=\"b\"/><itemref idref=\"a\"/></spine></package>","OPS/a.xhtml","<html><body><h1>둘째</h1><p>뒤쪽 본문</p></body></html>","OPS/b.xhtml","<html><head><title>숨김</title></head><body><h1>첫째</h1><p>앞쪽 본문</p></body></html>");JSONObject b=BookParser.parse(f,"책.epub","epub");assertEquals("첫째",b.getJSONArray("chapters").getJSONObject(0).getString("title"));assertEquals("첫째",b.getJSONArray("paragraphs").getString(0));assertFalse(b.getJSONArray("paragraphs").toString().contains("숨김"));f.delete();}
 @Test public void importDocx()throws Exception{File f=file(".docx");zip(f,"word/document.xml","<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>제목</w:t></w:r></w:p><w:p><w:r><w:t>알아</w:t></w:r><w:r><w:t>차림</w:t></w:r></w:p></w:body></w:document>");JSONObject b=BookParser.parse(f,"책.docx","docx");assertEquals("알아차림",b.getJSONArray("paragraphs").getString(1));assertEquals("제목",b.getJSONArray("chapters").getJSONObject(0).getString("title"));f.delete();}
 @Test public void importPdf()throws Exception{PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().getTargetContext());File f=file(".pdf");try(PDDocument doc=new PDDocument()){PDPage p=new PDPage();doc.addPage(p);try(PDPageContentStream s=new PDPageContentStream(doc,p)){s.beginText();s.setFont(PDType1Font.HELVETICA,14);s.newLineAtOffset(30,700);s.showText("Mindful reading");s.endText();}doc.save(f);}JSONObject b=BookParser.parse(f,"책.pdf","pdf");assertEquals(1,b.getInt("pageCount"));assertTrue(b.getJSONArray("paragraphs").toString().contains("Mindful reading"));f.delete();}
 @Test public void xmlRejectsDtdUtf16()throws Exception{try{BookParser.xml("<!DOCTYPE x [<!ENTITY a 'oops'>]><x>&a;</x>".getBytes(StandardCharsets.UTF_16));fail("DTD allowed");}catch(IOException expected){}}
 @Test public void appLaunchesAndRotates()throws Exception{
  android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();Context c=ins.getTargetContext();Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);MainActivity a=(MainActivity)ins.startActivitySync(i);
  try{String text="";for(int n=0;n<50;n++){Thread.sleep(100);CountDownLatch latch=new CountDownLatch(1);String[] val={""};ins.runOnMainSync(()->{WebView w=(WebView)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);w.evaluateJavascript("document.getElementById('bookTitle')?.textContent",s->{val[0]=s;latch.countDown();});});latch.await(2,TimeUnit.SECONDS);text=val[0];if(text.contains("한 문장"))break;}assertTrue(text,text.contains("한 문장"));ins.runOnMainSync(()->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(600);assertFalse(a.isFinishing());}finally{ins.runOnMainSync(a::finish);}
 }
}
