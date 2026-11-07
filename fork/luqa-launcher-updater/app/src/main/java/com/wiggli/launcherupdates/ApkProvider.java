package com.wiggli.launcherupdates;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class ApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !"launcher.apk".equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException("Unknown update file");
        }
        File file = new File(getContext().getCacheDir(), "luqa-launcher.apk");
        if (!file.isFile()) throw new FileNotFoundException("Update is not ready");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        File file = new File(getContext().getCacheDir(), "luqa-launcher.apk");
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("luqa-launcher.apk");
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.isFile() ? file.length() : 0L);
            else row.add(null);
        }
        return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
