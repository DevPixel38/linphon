package org.linphone;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by devpixel38 on 3/20/17.
 */

public class Cache {
    private static Cache defaultInstance;
    private static Object _lock = new Object();

    public static Cache getDefaultInstance(Context context) {
        synchronized (_lock) {
            if (defaultInstance == null)
                defaultInstance = new Cache(context, false);
        }
        return defaultInstance;
    }

    private Context applicationContext;
    private Gson gson;
    private File cacheFolder;

    /**
     * Gets default Gson object for this class
     *
     * @return Default {@link Gson} instance
     */
    private Gson getDefaultGson() {
        if (this.gson == null)
            this.gson = new Gson();
        return this.gson;
    }

    /**
     * Gets cache folder
     *
     * @return {@link File} instance or null if not initialized
     */
    private File getCacheFolder() {
        return this.cacheFolder;
    }

    /**
     * Sets application context value
     *
     * @param context {@link Context} instance
     */
    private void setApplicationContext(@NonNull Context context) {
        this.applicationContext = context;
    }

    /**
     * Gets application context
     *
     * @return Application context or null if not found
     */
    private Context getApplicationContext() {
        return this.applicationContext;
    }

    /**
     * Creates a new instance of {@link Cache} class
     *
     * @param applicationContext Application context
     * @param clearIfExists      Whether to clear cache contents
     */
    public Cache(@NonNull Context applicationContext, boolean clearIfExists) {
        this.setApplicationContext(applicationContext);
        this.initCacheDir(clearIfExists);
    }

    /**
     * Initializes Cache folder
     *
     * @param clearIfExists Whether to clear cache contents
     */
    private void initCacheDir(boolean clearIfExists) {
        this.cacheFolder = new File(this.getApplicationContext().getCacheDir().getPath() + File.separator + this.getApplicationContext().getPackageName());

        // Check if cache folder exists
        if (!this.cacheFolder.exists()) {
            this.cacheFolder.mkdir();
        } else {
            if (clearIfExists) {
                this.clearCache();
            }
        }
    }

    /**
     * Writes object to cache file
     *
     * @param tag    Cache tag
     * @param object Object to write
     * @return True if written to cache, else false
     */
    public boolean writeObject(String tag, Object object) {
        BufferedWriter writer = null;
        boolean isSuccess = true;
        try {
            File outputFile = new File(this.getCacheFolder().getPath() + File.separator + tag);
            String json = this.getDefaultGson().toJson(object);
            writer = new BufferedWriter(new FileWriter(outputFile));
            writer.write(json);
            writer.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
            isSuccess = false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return isSuccess;
    }

    /**
     * Reads cache from file
     *
     * @param tag        Cache tag
     * @param outputType Type of output
     * @return Instance of outputType or null if cache not found or {@link IOException} raised
     * this case data needs to be fetched from server
     */
    @Nullable
    public Object readObject(String tag, @NonNull Class outputType) {
        BufferedReader reader = null;
        try {
            File inputFile = new File(this.getCacheFolder().getPath() + File.separator + tag);
            if (!inputFile.exists()) {
                return null;
            }
            reader = new BufferedReader(new FileReader(inputFile));
            String line = "";
            String fullString = "";
            while ((line = reader.readLine()) != null) {
                fullString += line;
            }
            if (fullString.trim().length() == 0)
                return null;

            return this.getDefaultGson().fromJson(fullString, outputType);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    ignored.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * Checks if cache is available for specific Tag
     *
     * @param tag Tag to check
     * @return True if found, else false
     */
    public boolean hasTag(String tag) {
        if (this.cacheFolder == null)
            return false;
        else if (!this.cacheFolder.exists()) {
            return false;
        }
        for (File file : this.cacheFolder.listFiles()) {
            if (file.getName().equals(tag))
                return true;
        }
        return false;
    }

    /**
     * Deletes a specific cache file from cache with given Tag
     *
     * @param tag Identifier of the cache file
     */
    public void deleteCache(String tag) {
        try {
            for (File file : this.getCacheFolder().listFiles()) {
                if (file.getName().equals(tag)) {
                    file.delete();
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Clears cache from disk
     */
    private void clearCache() {

        try {
            for (File file : this.getCacheFolder().listFiles()) {
                file.delete();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}