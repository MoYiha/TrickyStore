package cleveres.tricky.cleverestech.keystore;

import org.junit.Test;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class XMLParserBugTest {

    @Test
    public void testMixedContent() throws Exception {
        String xml = "<Root><Data>Part1<Inner/>Part2</Data></Root>";

        XMLParser parser = new XMLParser(new StringReader(xml));
        String extracted = parser.obtainPath("Root.Data").get("text");

        assertEquals("Extracted text content mismatch", "Part1Part2", extracted);
    }

    @Test
    public void malformedPathIndexesAreRejected() throws Exception {
        XMLParser parser = new XMLParser(new StringReader("<Root><Data>value</Data></Root>"));

        assertEquals(0, parser.getChildCount("Root.Data[-1]", "Child"));
        assertEquals(0, parser.getChildCount("Root.Data[invalid]", "Child"));
        assertThrows(RuntimeException.class, () -> parser.obtainPath("Root.Data[0]trailing"));
        assertThrows(RuntimeException.class, () -> parser.obtainPath("Root.Data."));
    }

    @Test
    public void contentAfterTheRootElementIsRejected() {
        assertThrows(
                Exception.class,
                () -> new XMLParser(new StringReader("<Root/><Second/>")));
        assertThrows(
                Exception.class,
                () -> new XMLParser(new StringReader("<Root/>unexpected")));
    }
}
