package kr.co.mindfulebook;

import android.text.Html;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

public final class BookParser {
    static final int MAX_TEXT=5_000_000;
    public static byte[] read(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[16384];int n,total=0;
        while((n=in.read(b))!=-1) {total+=n;if(total>limit)throw new IOException("파일이 너무 큽니다. 작은 파일로 나누어 가져오십시오.");out.write(b,0,n);}return out.toByteArray();
    }
    static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        if(raw.contains("<!DOCTYPE") || raw.contains("<!ENTITY")) throw new IOException("DTD가 포함된 문서는 지원하지 않습니다.");
        DocumentBuilder builder=f.newDocumentBuilder();
        builder.setEntityResolver((a,b)->new org.xml.sax.InputSource(new StringReader("")));
        return builder.parse(new ByteArrayInputStream(bytes));
    }
    static byte[] entry(ZipFile zip,String name) throws Exception {
        ZipEntry e=zip.getEntry(name); if(e==null) throw new IOException("문서 구성 파일이 없습니다: "+name);
        try(InputStream in=zip.getInputStream(e)){return read(in,12_000_000);}
    }
    static class Builder {
        JSONArray paragraphs=new JSONArray(), chapters=new JSONArray(), pages=new JSONArray(); int chars;
        void add(String title,String text,int page) throws Exception {
            List<String> chunks=TextTools.chunks(text);
            if(!title.isEmpty()) chapters.put(new JSONObject().put("title",title).put("index",paragraphs.length()).put("page",page));
            for(String s:chunks){chars+=s.length();if(chars>MAX_TEXT)throw new IOException("본문이 너무 깁니다. 문서를 나누어 가져오십시오.");paragraphs.put(s);pages.put(page);}
        }
    }
    public static JSONObject parse(File file,String name,String type) throws Exception {
        Builder b=new Builder(); int pageCount=0;
        switch(type) {
            case "txt": {
                String text; try(InputStream in=new FileInputStream(file)){text=TextTools.decode(read(in,20_000_000));}
                int section=0;
                for(String p:text.replace("\r", "").split("\n+")) {
                    String t=p.trim(); boolean heading=t.length()<90 && t.matches("^(제\\s*\\d+\\s*[부장절].*|[0-9]+[.)]\\s+.*|#{1,3}\\s+.*)$");
                    if(heading) section++;
                    b.add(heading?t:"",t,0);
                }
                if(section==0)b.chapters.put(new JSONObject().put("title","본문").put("index",0).put("page",0));
                break;
            }
            case "docx": try(ZipFile zip=new ZipFile(file)) {
                Document d=xml(entry(zip,"word/document.xml")); NodeList ps=d.getElementsByTagNameNS("*","p");
                for(int i=0;i<ps.getLength();i++) {
                    Element p=(Element)ps.item(i);StringBuilder text=new StringBuilder(); collectDocx(p,text);
                    NodeList styles=p.getElementsByTagNameNS("*","pStyle");boolean heading=false;
                    if(styles.getLength()>0){String st=((Element)styles.item(0)).getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","val");heading=st.toLowerCase(Locale.ROOT).startsWith("heading") || st.startsWith("제목");}
                    String s=text.toString();b.add(heading?s:"",s,0);
                }
                break;
            }
            case "epub": try(ZipFile zip=new ZipFile(file)) {
                if(zip.getEntry("META-INF/encryption.xml")!=null) throw new IOException("암호화된 EPUB은 지원하지 않습니다. DRM 없는 파일을 선택하십시오.");
                Enumeration<? extends ZipEntry> entries=zip.entries();long total=0;int count=0;
                while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();total+=Math.max(0,e.getSize());if(++count>10000 || total>150_000_000)throw new IOException("전자책 압축 크기가 너무 큽니다.");}
                Document container=xml(entry(zip,"META-INF/container.xml"));NodeList roots=container.getElementsByTagNameNS("*","rootfile");
                if(roots.getLength()==0)throw new IOException("EPUB 구조를 읽을 수 없습니다.");
                String opf=((Element)roots.item(0)).getAttribute("full-path");Document d=xml(entry(zip,opf));
                Map<String,String> manifest=new HashMap<>();NodeList items=d.getElementsByTagNameNS("*","item");
                for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);manifest.put(item.getAttribute("id"),item.getAttribute("href"));}
                NodeList spine=d.getElementsByTagNameNS("*","itemref");
                for(int i=0;i<spine.getLength();i++){
                    Element ref=(Element)spine.item(i);if("no".equals(ref.getAttribute("linear")))continue;
                    String href=manifest.get(ref.getAttribute("idref"));if(href==null)throw new IOException("EPUB 읽기 순서가 손상되었습니다.");
                    String html=TextTools.decode(entry(zip,TextTools.resolveZip(opf,href)));
                    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").matcher(html);
                    String title=m.find()?Html.fromHtml(m.group(1),Html.FROM_HTML_MODE_LEGACY).toString().trim():"제 "+(i+1)+" 구역";
                    html=html.replaceAll("(?is)<(script|style|head)\\b[^>]*>.*?</\\1>","");
                    b.add(title,Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString(),0);
                }
                break;
            }
            case "pdf": try(PDDocument pdf=PDDocument.load(file)) {
                if(pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent())throw new IOException("암호화되었거나 텍스트 추출이 제한된 PDF입니다.");
                pageCount=pdf.getNumberOfPages();if(pageCount>3000)throw new IOException("PDF는 3,000쪽 이하로 나누어 가져오십시오.");PDFTextStripper stripper=new PDFTextStripper();stripper.setSortByPosition(true);
                for(int p=1;p<=pageCount;p++){stripper.setStartPage(p);stripper.setEndPage(p);b.add(p+"쪽",stripper.getText(pdf),p-1);}
                break;
            }
            default: throw new IOException("TXT·EPUB·PDF·DOCX 파일을 선택하십시오.");
        }
        if(b.paragraphs.length()==0 && !type.equals("pdf"))throw new IOException("읽을 수 있는 본문이 없습니다.");
        if(b.chapters.length()==0)b.chapters.put(new JSONObject().put("title","본문").put("index",0).put("page",0));
        return new JSONObject().put("title",name.replaceFirst("(?i)\\.(txt|epub|pdf|docx)$", "")).put("type",type).put("paragraphs",b.paragraphs).put("chapters",b.chapters).put("pages",b.pages).put("pageCount",pageCount);
    }
    static void collectDocx(Node n,StringBuilder text){
        String local=n.getLocalName();if("t".equals(local)){text.append(n.getTextContent());return;}
        if("tab".equals(local))text.append("  ");if("br".equals(local))text.append('\n');
        NodeList nodes=n.getChildNodes();for(int i=0;i<nodes.getLength();i++)collectDocx(nodes.item(i),text);
    }
}
