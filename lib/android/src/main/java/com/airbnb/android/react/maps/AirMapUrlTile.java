package com.airbnb.android.react.maps;

import android.content.Context;
import android.util.Log;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class AirMapUrlTile extends AirMapFeature {

  private static final String ORIGIN = AirMapUrlTile.class.getSimpleName();

  class Wrapper {
    private String urlTemplate;

    Wrapper(String urlTemplate) {
      this.urlTemplate = urlTemplate;
    }

    void setUrlTemplate(String urlTemplate) {
      this.urlTemplate = urlTemplate;
    }

    class AIRMapUrlTileProvider extends UrlTileProvider {

      AIRMapUrlTileProvider(int width, int height) {
        super(width, height);
      }

      @Override
      public synchronized URL getTileUrl(int x, int y, int zoom) {
        return getUrl(x, y, zoom, urlTemplate);
      }
    }

    class AIRMapUrlTile implements TileProvider {
      private static final int BUFFER_SIZE = 4 * 1024;
      private int width;
      private int height;
      private ReadableMap requestProperties;

      AIRMapUrlTile(int width, int height, ReadableMap requestProperties) {
        this.width = width;
        this.height = height;
        this.requestProperties = requestProperties;
      }

      private byte[] readTileImage(int x, int y, int zoom) {
        InputStream in = null;
        ByteArrayOutputStream buffer = null;
        URL url = getUrl(x, y, zoom, urlTemplate);
        if (url == null)
          return null;

        try {
          HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

          //connection.addRequestProperty("Authorization", "Basic xxxxxxxxxxxxxxx-token");
          for (Map.Entry<String, Object> entry : requestProperties.toHashMap().entrySet()) {
            connection.addRequestProperty(entry.getKey(), (String) entry.getValue());
          }

          connection.connect();
          in = connection.getInputStream();
          buffer = new ByteArrayOutputStream();

          byte[] data = new byte[BUFFER_SIZE];
          int nRead;
          while ((nRead = in.read(data, 0, BUFFER_SIZE)) != -1) {
            buffer.write(data, 0, nRead);
          }
          buffer.flush();
          return buffer.toByteArray();
        } catch (IOException | OutOfMemoryError e) {
          Log.e(ORIGIN, e.getMessage());
          return null;
        } finally {
          if (in != null) try {
            in.close();
          } catch (Exception e) {
            Log.e(ORIGIN, e.getMessage());
          }
          if (buffer != null) try {
            buffer.close();
          } catch (Exception e) {
            Log.e(ORIGIN, e.getMessage());
          }
        }
      }

      @Override
      public Tile getTile(int x, int y, int zoom) {
        byte[] image = readTileImage(x, y, zoom);
        return image == null ? TileProvider.NO_TILE : new Tile(this.width, this.height, image);
      }
    }

    private String getTileUrl(int x, int y, int zoom, String urlTemplate) {
      return urlTemplate
        .replace("{x}", Integer.toString(x))
        .replace("{y}", Integer.toString(y))
        .replace("{z}", Integer.toString(zoom));
    }

    private URL getUrl(int x, int y, int zoom, String urlTemplate) {

      if (AirMapUrlTile.this.flipY) {
        y = (1 << zoom) - y - 1;
      }

      String s = getTileUrl(x, y, zoom, urlTemplate);

      if (AirMapUrlTile.this.maximumZ > 0 && zoom > maximumZ) {
        return null;
      }

      if (AirMapUrlTile.this.minimumZ > 0 && zoom < minimumZ) {
        return null;
      }

      try {
        return new URL(s);
      } catch (MalformedURLException e) {
        throw new AssertionError(e);
      }
    }
  }

  private TileOverlayOptions tileOverlayOptions;
  private TileOverlay tileOverlay;
  private Wrapper tileProvider;

  private String urlTemplate;
  private float zIndex;
  private float maximumZ;
  private float minimumZ;
  private boolean flipY;
  private ReadableMap requestProperties;

  public AirMapUrlTile(Context context) {
    super(context);
  }

  void setUrlTemplate(String urlTemplate) {
    this.urlTemplate = urlTemplate;
    if (tileProvider != null) {
      tileProvider.setUrlTemplate(urlTemplate);
    }
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  public void setZIndex(float zIndex) {
    this.zIndex = zIndex;
    if (tileOverlay != null) {
      tileOverlay.setZIndex(zIndex);
    }
  }

  void setMaximumZ(float maximumZ) {
    this.maximumZ = maximumZ;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  void setMinimumZ(float minimumZ) {
    this.minimumZ = minimumZ;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  void setFlipY(boolean flipY) {
    this.flipY = flipY;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  private TileOverlayOptions getTileOverlayOptions() {
    if (tileOverlayOptions == null) {
      tileOverlayOptions = createTileOverlayOptions();
    }
    return tileOverlayOptions;
  }

  void setRequestProperties(ReadableMap requestProperties) {
    this.requestProperties = requestProperties;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  private TileOverlayOptions createTileOverlayOptions() {
    TileOverlayOptions options = new TileOverlayOptions();
    options.zIndex(zIndex);
    this.tileProvider = new Wrapper(this.urlTemplate);
    if (requestProperties == null) {
      Wrapper.AIRMapUrlTileProvider airMapUrlTileProvider = this.tileProvider.new AIRMapUrlTileProvider(256, 256);
      options.tileProvider(airMapUrlTileProvider);
    } else {
      Wrapper.AIRMapUrlTile airMapUrlTile = this.tileProvider.new AIRMapUrlTile(256, 256, requestProperties);
      options.tileProvider(airMapUrlTile);
    }

    return options;
  }

  @Override
  public Object getFeature() {
    return tileOverlay;
  }

  @Override
  public void addToMap(GoogleMap map) {
    this.tileOverlay = map.addTileOverlay(getTileOverlayOptions());
  }

  @Override
  public void removeFromMap(GoogleMap map) {
    tileOverlay.remove();
  }
}
