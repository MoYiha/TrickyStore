package android.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Stack;
import org.json.JSONObject;
import org.json.JSONTokener;

public class JsonReader implements Closeable {
    private final Stack<Iterator<String>> keyIterators = new Stack<>();
    private final Stack<JSONObject> objects = new Stack<>();
    private JSONObject currentObject;
    private String pendingName;
    private IOException deferredException;

    public JsonReader(Reader in) {
        try {
            // Read all into a StringBuilder
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                sb.append((char) ch);
            }
            if (sb.length() == 0) return;

            Object val = new JSONTokener(sb.toString()).nextValue();
            if (val instanceof JSONObject) {
                currentObject = (JSONObject) val;
            } else {
                throw new IOException("Root must be object");
            }
        } catch (Exception e) {
            deferredException = new IOException(e);
        }
    }

    public void beginObject() throws IOException {
        if (deferredException != null) throw deferredException;

        if (pendingName != null) {
            // Entering nested object
            if (objects.isEmpty()) throw new IOException("No parent object");
            JSONObject parent = objects.peek();
            JSONObject child = parent.optJSONObject(pendingName);
            if (child == null) throw new IOException("Not an object: " + pendingName);
            currentObject = child;
            pendingName = null;
        } else {
            // Root object or already set currentObject
            if (!objects.isEmpty()) {
                 // If pendingName is null and objects not empty, maybe we are confused?
                 // But for root object, pendingName is null.
            }
        }

        if (currentObject == null) throw new IOException("No object to begin");

        objects.push(currentObject);
        keyIterators.push(currentObject.keys());
    }

    public void endObject() throws IOException {
        if (deferredException != null) throw deferredException;
        if (objects.isEmpty()) throw new IOException("Stack empty");
        objects.pop();
        keyIterators.pop();
        if (!objects.isEmpty()) {
            currentObject = objects.peek();
        } else {
            currentObject = null;
        }
    }

    public boolean hasNext() throws IOException {
        if (deferredException != null) throw deferredException;
        if (keyIterators.isEmpty()) return false;
        return keyIterators.peek().hasNext();
    }

    public String nextName() throws IOException {
        if (deferredException != null) throw deferredException;
        if (keyIterators.isEmpty()) throw new IOException("No object");
        pendingName = keyIterators.peek().next();
        return pendingName;
    }

    public String nextString() throws IOException {
        if (deferredException != null) throw deferredException;
        if (pendingName == null) throw new IOException("Call nextName() first");
        if (objects.isEmpty()) throw new IOException("No object");
        String val = objects.peek().optString(pendingName);
        pendingName = null;
        return val;
    }

    public void skipValue() throws IOException {
        if (deferredException != null) throw deferredException;
        pendingName = null; // Just ignore the value
    }

    public void close() throws IOException {
        // no-op
    }
}
