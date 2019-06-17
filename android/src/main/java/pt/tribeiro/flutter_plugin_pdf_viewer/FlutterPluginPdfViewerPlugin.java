package pt.tribeiro.flutter_plugin_pdf_viewer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;


import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlutterPluginPdfViewerPlugin
 */
public class FlutterPluginPdfViewerPlugin implements MethodCallHandler {
    private static Registrar instance;
    private ExecutorService executor;
    private static Activity activity;

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_plugin_pdf_viewer");
        instance = registrar;
        activity = registrar.activity();
        channel.setMethodCallHandler(new FlutterPluginPdfViewerPlugin());
    }

    @Override
    public void onMethodCall(final MethodCall call, final Result result) {
        switch (call.method) {
            case "getNumberOfPages":
                getNumberOfPages((String) call.argument("filePath"), result);
                break;
            case "getPage":
                getPage((String) call.argument("filePath"), (int) call.argument("pageNumber"), result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private synchronized void io(@NonNull Runnable runnable) {
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }
        executor.execute(runnable);
    }

    private void ui(@NonNull Runnable runnable) {
        activity.runOnUiThread(runnable);
    }


    private void getNumberOfPages(final String filePath, final Result result) {

        io(new Runnable() {
            @Override
            public void run() {
                File pdf = new File(filePath);
                try {
                    PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
                    final int pageCount = renderer.getPageCount();
                    ui(new Runnable() {
                        @Override
                        public void run() {
                            result.success(String.format("%d", pageCount));
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private String createTempPreview(Bitmap bmp, String name, int page) {
        final String filePath = name.substring(name.lastIndexOf('.'));
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


    private void getPage(final String filePath, final int page, final Result result) {
        io(new Runnable() {
            @Override
            public void run() {
                File pdf = new File(filePath);
                try {
                    PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY));
                    final int pageCount = renderer.getPageCount();
                    int pageNumber = page > pageCount ? pageCount : page;

                    PdfRenderer.Page page = renderer.openPage(--pageNumber);

                    double width = instance.activity().getResources().getDisplayMetrics().densityDpi * page.getWidth();
                    double height = instance.activity().getResources().getDisplayMetrics().densityDpi * page.getHeight();
                    final double docRatio = width / height;

                    width = 2048;
                    height = (int) (width / docRatio);
                    final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
                    // Change background to white
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);
                    // Render to bitmap
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    final int tempPage = pageNumber;
                    try {
                        ui(new Runnable() {
                            @Override
                            public void run() {
                                result.success(createTempPreview(bitmap, filePath, tempPage));
                            }
                        });
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
            }
        });


    }
}