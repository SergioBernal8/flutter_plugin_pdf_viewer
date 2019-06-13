package pt.tribeiro.flutter_plugin_pdf_viewer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

/**
 * FlutterPluginPdfViewerPlugin
 */
public class FlutterPluginPdfViewerPlugin implements MethodCallHandler {
    private static Registrar instance;
    private static  Handler handler;
    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_plugin_pdf_viewer");
        instance = registrar;
        channel.setMethodCallHandler(new FlutterPluginPdfViewerPlugin());
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onMethodCall(final MethodCall call, final Result result) {

        switch (call.method) {
            case "getNumberOfPages":
                result.success(getNumberOfPages((String) call.argument("filePath")));
                break;
            case "getPage": {

                Future task = new RunnableFuture() {
                    String page;
                    @Override
                    public void run() {
                       page = getPage((String) call.argument("filePath"), (int) call.argument("pageNumber"));
                    }

                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return false;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return false;
                    }

                    @Override
                    public Object get() throws ExecutionException, InterruptedException {
                        return page;
                    }

                    @Override
                    public Object get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
                        return page;
                    }
                };
                ((RunnableFuture) task).run();
                try {
                    result.success(task.get());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
            default:
                result.notImplemented();
                break;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {

            }
        });
    }

    private String getNumberOfPages(String filePath) {
        File pdf = new File(filePath);
        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
            Bitmap bitmap;
            final int pageCount = renderer.getPageCount();


            return String.format("%d", pageCount);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private String createTempPreview(Bitmap bmp, String name, int page) {
        String filePath = name.substring(name.lastIndexOf('/') + 1);
        filePath = name.substring(name.lastIndexOf('.'));
        File file;
        try {
            String fileName = String.format("%s-%d.png", filePath, page);
            file = File.createTempFile(fileName, null, instance.context().getCacheDir());
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return file.getAbsolutePath();
    }


    private String getPage(String filePath, int pageNumber) {
        File pdf = new File(filePath);
        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
            final int pageCount = renderer.getPageCount();
            if (pageNumber > pageCount) {
                pageNumber = pageCount;
            }

            PdfRenderer.Page page = renderer.openPage(--pageNumber);

            double width = instance.activity().getResources().getDisplayMetrics().densityDpi * page.getWidth();
            double height = instance.activity().getResources().getDisplayMetrics().densityDpi * page.getHeight();
            final double docRatio = width / height;

            width = 2048;
            height = (int) (width / docRatio);
            Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
            // Change background to white
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            // Render to bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            try {
                return createTempPreview(bitmap, filePath, pageNumber);
            } finally {
                // close the page
                page.close();
                // close the renderer
                renderer.close();
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

        return null;
    }
}