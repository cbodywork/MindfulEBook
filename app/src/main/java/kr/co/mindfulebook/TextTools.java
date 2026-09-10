package kr.co.mindfulebook;

import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.net.URI;
import java.util.*;

public final class TextTools {
    public static String decode(byte[] bytes) {
        if (bytes.length>=2 && ((bytes[0]&255)==255 && (bytes[1]&255)==254)) return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if (bytes.length>=2 && ((bytes[0]&255)==254 && (bytes[1]&255)==255)) return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", ""); }
        catch(Exception e) { return new String(bytes,Charset.forName("MS949")); }
    }
    public static List<String> chunks(String text) {
        List<String> out=new ArrayList<>();
        for(String para:text.replace("\r", "").replace('\u00a0',' ').split("\n+")) {
            para=para.trim();
            while(!para.isEmpty()) {
                int cut=Math.min(650,para.length());
                if(cut<para.length()) {
                    if(Character.isHighSurrogate(para.charAt(cut-1))) cut--;
                    int space=para.lastIndexOf(' ',cut); if(space>cut/2) cut=space;
                }
                out.add(para.substring(0,cut).trim()); para=para.substring(cut).trim();
            }
        }
        return out;
    }
    public static String resolveZip(String base,String href) throws Exception {
        URI resolved=new URI(base).resolve(href).normalize();
        String path=resolved.getPath();
        if(resolved.isAbsolute() || path==null || path.startsWith("/") || path.startsWith("../") || path.contains("\\")) throw new Exception("전자책 내부 경로가 올바르지 않습니다.");
        return path;
    }
    public static String pronounce(String text,String dictionary) {
        Map<String,String> map=new HashMap<>();
        for(String line:dictionary.split("\n")) { int p=line.indexOf('='); if(p>0 && !line.substring(p+1).trim().isEmpty()) map.put(line.substring(0,p).trim(),line.substring(p+1).trim()); }
        List<String> keys=new ArrayList<>(map.keySet()); keys.sort((a,b)->Integer.compare(b.length(),a.length()));
        StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();) {
            String match=null; for(String key:keys) if(text.startsWith(key,i)){match=key;break;}
            if(match==null) out.append(text.charAt(i++)); else { out.append(map.get(match)); i+=match.length(); }
        }
        return out.toString();
    }
}
