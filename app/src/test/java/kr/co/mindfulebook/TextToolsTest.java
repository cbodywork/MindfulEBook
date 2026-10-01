package kr.co.mindfulebook;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class TextToolsTest {
 @Test public void koreanLegacyEncoding(){String s="알아차림 약선치유";assertEquals(s,TextTools.decode(s.getBytes(java.nio.charset.Charset.forName("MS949"))));assertEquals(s,TextTools.decode(s.getBytes(StandardCharsets.UTF_8)));}
 @Test public void longestDictionaryTermAndNoCascade(){assertEquals("마이크로바이옴 에이",TextTools.pronounce("microbiome A","micro=마이크로\nmicrobiome=마이크로바이옴\nA=에이\n에이=잘못"));}
 @Test public void chunksPreserveSurrogates(){String s="가".repeat(649)+"😀"+"나".repeat(100);assertEquals(s,String.join("",TextTools.chunks(s)));for(String c:TextTools.chunks(s))assertFalse(Character.isHighSurrogate(c.charAt(c.length()-1)));}
 @Test public void epubRelativePaths() throws Exception {assertEquals("OPS/Text/1.xhtml",TextTools.resolveZip("OPS/package.opf","Text/1.xhtml#part"));assertEquals("Text/a b.xhtml",TextTools.resolveZip("OPS/package.opf","../Text/a%20b.xhtml"));}
 @Test(expected=Exception.class) public void refusesZipTraversal() throws Exception {TextTools.resolveZip("OPS/package.opf","../../outside");}
 @Test(expected=Exception.class) public void refusesExternalEpub() throws Exception {TextTools.resolveZip("OPS/package.opf","https://example.com/book");}
}
