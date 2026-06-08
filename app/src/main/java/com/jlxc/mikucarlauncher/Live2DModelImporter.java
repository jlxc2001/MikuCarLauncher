package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class Live2DModelImporter {
    public static final String PREF_MODEL_LABEL = "live2d_model_label";
    public static final String PREF_MOTION_COUNT = "live2d_motion_count";

    private Live2DModelImporter() {
    }

    public static class Result {
        public final boolean success;
        public final String modelPath;
        public final String label;
        public final String message;
        public final int motionCount;

        Result(boolean success, String modelPath, String label, String message) {
            this(success, modelPath, label, message, 0);
        }

        Result(boolean success, String modelPath, String label, String message, int motionCount) {
            this.success = success;
            this.modelPath = modelPath;
            this.label = label;
            this.message = message;
            this.motionCount = motionCount;
        }
    }

    public static Result importFromTreeUri(Context context, Uri treeUri) {
        if (context == null || treeUri == null) {
            return new Result(false, "", "", "没有选择 Live2D 模型文件夹");
        }

        File outRoot = new File(context.getFilesDir(), "live2d/selected_model");
        try {
            deleteRecursively(outRoot);
            if (!outRoot.mkdirs() && !outRoot.exists()) {
                return new Result(false, "", "", "无法创建模型缓存目录");
            }

            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
            DocumentMeta rootMeta = queryMeta(context, rootDocUri);
            String label = rootMeta.name != null && rootMeta.name.length() > 0 ? rootMeta.name : "Live2D模型";

            copyDocumentTree(context, treeUri, rootDocUri, outRoot);

            File modelFile = findModelFile(outRoot);
            if (modelFile == null) {
                return new Result(false, "", label, "没有在文件夹里找到 model3.json 或 model.json");
            }

            int motionCount = countMotionFiles(outRoot);
            String message = "已导入：" + label;
            if (motionCount > 0) {
                message += "，读取到 " + motionCount + " 个动作文件";
            } else {
                message += "，未发现动作文件";
            }

            return new Result(true, modelFile.getAbsolutePath(), label, message, motionCount);
        } catch (Throwable t) {
            return new Result(false, "", "", "导入失败：" + t.getMessage());
        }
    }

    private static void copyDocumentTree(Context context, Uri treeUri, Uri docUri, File outDir) throws Exception {
        DocumentMeta meta = queryMeta(context, docUri);
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(meta.mimeType)) {
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getDocumentId(docUri)
            );

            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                        },
                        null,
                        null,
                        null
                );

                if (cursor == null) {
                    return;
                }

                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    String childName = sanitizeFileName(cursor.getString(1));
                    String childMime = cursor.getString(2);

                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        File childDir = new File(outDir, childName);
                        copyDocumentTree(context, treeUri, childUri, childDir);
                    } else {
                        copyFile(context, childUri, new File(outDir, childName));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else {
            File outFile = new File(outDir, sanitizeFileName(meta.name));
            copyFile(context, docUri, outFile);
        }
    }

    private static void copyFile(Context context, Uri src, File outFile) throws Exception {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(src);
            if (input == null) {
                return;
            }
            output = new FileOutputStream(outFile);
            byte[] buffer = new byte[32 * 1024];
            int len;
            while ((len = input.read(buffer)) >= 0) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int countMotionFiles(File root) {
        if (root == null || !root.exists()) {
            return 0;
        }

        if (root.isFile()) {
            String name = root.getName().toLowerCase();
            if (name.endsWith(".motion3.json")
                    || name.endsWith(".mtn")
                    || name.endsWith(".motion.json")) {
                return 1;
            }
            return 0;
        }

        int count = 0;
        File[] list = root.listFiles();
        if (list != null) {
            for (File f : list) {
                count += countMotionFiles(f);
            }
        }
        return count;
    }

    private static File findModelFile(File root) {
        if (root == null || !root.exists()) {
            return null;
        }

        File model3 = findBySuffix(root, ".model3.json");
        if (model3 != null) {
            return model3;
        }

        File modelJson = findByName(root, "model.json");
        if (modelJson != null) {
            return modelJson;
        }

        return findBySuffix(root, ".json");
    }

    private static File findByName(File dir, String name) {
        File[] list = dir.listFiles();
        if (list == null) {
            return null;
        }

        for (File f : list) {
            if (f.isFile() && name.equalsIgnoreCase(f.getName())) {
                return f;
            }
        }

        for (File f : list) {
            if (f.isDirectory()) {
                File hit = findByName(f, name);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static File findBySuffix(File dir, String suffix) {
        File[] list = dir.listFiles();
        if (list == null) {
            return null;
        }

        for (File f : list) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(suffix)) {
                return f;
            }
        }

        for (File f : list) {
            if (f.isDirectory()) {
                File hit = findBySuffix(f, suffix);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static DocumentMeta queryMeta(Context context, Uri docUri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    docUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return new DocumentMeta(cursor.getString(0), cursor.getString(1));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new DocumentMeta("Live2D模型", DocumentsContract.Document.MIME_TYPE_DIR);
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.length() == 0) {
            return "unnamed";
        }
        return name.replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_");
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) {
                for (File child : list) {
                    deleteRecursively(child);
                }
            }
        }
        try {
            f.delete();
        } catch (Throwable ignored) {
        }
    }

    private static class DocumentMeta {
        final String name;
        final String mimeType;

        DocumentMeta(String name, String mimeType) {
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
        }
    }
}
