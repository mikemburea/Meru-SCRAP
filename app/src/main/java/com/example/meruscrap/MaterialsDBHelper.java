package com.example.meruscrap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.example.meruscrap.Material;

import java.util.ArrayList;
import java.util.List;

public class MaterialsDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "MaterialsDBHelper";

    // Database Info
    private static final String DATABASE_NAME = "MeruScrapDB";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_MATERIALS = "materials";

    // Materials Table Columns
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_PRICE_PER_KG = "price_per_kg";
    private static final String KEY_ICON = "icon";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_CREATED_AT = "created_at";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_IS_ACTIVE = "is_active";

    // Create Materials Table SQL
    private static final String CREATE_MATERIALS_TABLE =
            "CREATE TABLE " + TABLE_MATERIALS + " (" +
                    KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_NAME + " TEXT NOT NULL UNIQUE, " +
                    KEY_PRICE_PER_KG + " REAL NOT NULL, " +
                    KEY_ICON + " TEXT DEFAULT '🔩', " +
                    KEY_DESCRIPTION + " TEXT, " +
                    KEY_CREATED_AT + " INTEGER NOT NULL, " +
                    KEY_UPDATED_AT + " INTEGER NOT NULL, " +
                    KEY_IS_ACTIVE + " INTEGER DEFAULT 1" +
                    ");";

    // Singleton instance
    private static MaterialsDBHelper instance;

    public static synchronized MaterialsDBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new MaterialsDBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private MaterialsDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "Creating materials table");
        db.execSQL(CREATE_MATERIALS_TABLE);

        // Insert default materials
        insertDefaultMaterials(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < newVersion) {
            // For now, just drop and recreate
            // In production, you'd want to migrate data
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_MATERIALS);
            onCreate(db);
        }
    }

    // CRUD Operations

    /**
     * Insert a new material
     */
    public long insertMaterial(Material material) {
        SQLiteDatabase db = this.getWritableDatabase();
        long result = -1;

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_NAME, material.getName());
            values.put(KEY_PRICE_PER_KG, material.getPricePerKg());
            values.put(KEY_ICON, material.getIcon());
            values.put(KEY_DESCRIPTION, material.getDescription());
            values.put(KEY_CREATED_AT, material.getCreatedAt());
            values.put(KEY_UPDATED_AT, material.getUpdatedAt());
            values.put(KEY_IS_ACTIVE, material.isActive() ? 1 : 0);

            result = db.insert(TABLE_MATERIALS, null, values);

            if (result != -1) {
                material.setId(result);
                Log.d(TAG, "Material inserted: " + material.getName() + " with ID: " + result);
            } else {
                Log.e(TAG, "Failed to insert material: " + material.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error inserting material: " + e.getMessage());
        }

        return result;
    }

    /**
     * Get all active materials
     */
    public List<Material> getAllMaterials() {
        List<Material> materials = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_MATERIALS +
                " WHERE " + KEY_IS_ACTIVE + " = 1 " +
                " ORDER BY " + KEY_NAME + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(selectQuery, null);

            if (cursor.moveToFirst()) {
                do {
                    Material material = cursorToMaterial(cursor);
                    materials.add(material);
                } while (cursor.moveToNext());
            }

            Log.d(TAG, "Retrieved " + materials.size() + " materials");
        } catch (Exception e) {
            Log.e(TAG, "Error getting materials: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return materials;
    }

    /**
     * Get material by ID
     */
    public Material getMaterialById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        Material material = null;

        try {
            cursor = db.query(TABLE_MATERIALS, null,
                    KEY_ID + " = ? AND " + KEY_IS_ACTIVE + " = 1",
                    new String[]{String.valueOf(id)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                material = cursorToMaterial(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting material by ID: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return material;
    }

    /**
     * Get material by name
     */
    public Material getMaterialByName(String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        Material material = null;

        try {
            cursor = db.query(TABLE_MATERIALS, null,
                    KEY_NAME + " = ? AND " + KEY_IS_ACTIVE + " = 1",
                    new String[]{name},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                material = cursorToMaterial(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting material by name: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return material;
    }

    /**
     * Update material
     */
    public int updateMaterial(Material material) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = 0;

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_NAME, material.getName());
            values.put(KEY_PRICE_PER_KG, material.getPricePerKg());
            values.put(KEY_ICON, material.getIcon());
            values.put(KEY_DESCRIPTION, material.getDescription());
            values.put(KEY_UPDATED_AT, System.currentTimeMillis());
            values.put(KEY_IS_ACTIVE, material.isActive() ? 1 : 0);

            result = db.update(TABLE_MATERIALS, values,
                    KEY_ID + " = ?",
                    new String[]{String.valueOf(material.getId())});

            Log.d(TAG, "Material updated: " + material.getName() + ", rows affected: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error updating material: " + e.getMessage());
        }

        return result;
    }

    /**
     * Soft delete material (set inactive)
     */
    public int deleteMaterial(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = 0;

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_IS_ACTIVE, 0);
            values.put(KEY_UPDATED_AT, System.currentTimeMillis());

            result = db.update(TABLE_MATERIALS, values,
                    KEY_ID + " = ?",
                    new String[]{String.valueOf(id)});

            Log.d(TAG, "Material soft deleted, ID: " + id + ", rows affected: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting material: " + e.getMessage());
        }

        return result;
    }

    /**
     * Hard delete material (permanently remove)
     */
    public int hardDeleteMaterial(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int result = 0;

        try {
            result = db.delete(TABLE_MATERIALS, KEY_ID + " = ?",
                    new String[]{String.valueOf(id)});

            Log.d(TAG, "Material hard deleted, ID: " + id + ", rows affected: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error hard deleting material: " + e.getMessage());
        }

        return result;
    }

    /**
     * Search materials by name
     */
    public List<Material> searchMaterials(String searchTerm) {
        List<Material> materials = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_MATERIALS +
                " WHERE " + KEY_NAME + " LIKE ? AND " + KEY_IS_ACTIVE + " = 1 " +
                " ORDER BY " + KEY_NAME + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(selectQuery, new String[]{"%" + searchTerm + "%"});

            if (cursor.moveToFirst()) {
                do {
                    Material material = cursorToMaterial(cursor);
                    materials.add(material);
                } while (cursor.moveToNext());
            }

            Log.d(TAG, "Search for '" + searchTerm + "' returned " + materials.size() + " materials");
        } catch (Exception e) {
            Log.e(TAG, "Error searching materials: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return materials;
    }

    /**
     * Get materials count
     */
    public int getMaterialsCount() {
        String query = "SELECT COUNT(*) FROM " + TABLE_MATERIALS + " WHERE " + KEY_IS_ACTIVE + " = 1";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        int count = 0;

        try {
            cursor = db.rawQuery(query, null);
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting materials count: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return count;
    }

    /**
     * Check if material name exists
     */
    public boolean materialNameExists(String name, long excludeId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean exists = false;

        try {
            String query = "SELECT 1 FROM " + TABLE_MATERIALS +
                    " WHERE " + KEY_NAME + " = ? AND " + KEY_IS_ACTIVE + " = 1";
            String[] args;

            if (excludeId > 0) {
                query += " AND " + KEY_ID + " != ?";
                args = new String[]{name, String.valueOf(excludeId)};
            } else {
                args = new String[]{name};
            }

            cursor = db.rawQuery(query, args);
            exists = cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking material name existence: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return exists;
    }

    /**
     * Helper method to convert cursor to Material object
     */
    private Material cursorToMaterial(Cursor cursor) {
        return new Material(
                cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)),
                cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_PRICE_PER_KG)),
                cursor.getString(cursor.getColumnIndexOrThrow(KEY_ICON)),
                cursor.getString(cursor.getColumnIndexOrThrow(KEY_DESCRIPTION)),
                cursor.getLong(cursor.getColumnIndexOrThrow(KEY_CREATED_AT)),
                cursor.getLong(cursor.getColumnIndexOrThrow(KEY_UPDATED_AT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_ACTIVE)) == 1
        );
    }

    /**
     * Insert default materials when database is first created
     */
    private void insertDefaultMaterials(SQLiteDatabase db) {
        Log.d(TAG, "Inserting default materials");

        Material[] defaultMaterials = {
                Material.createSteel(),
                Material.createAluminum(),
                Material.createCopper(),
                Material.createBrass(),
                Material.createIron(),
                Material.createLead()
        };

        for (Material material : defaultMaterials) {
            ContentValues values = new ContentValues();
            values.put(KEY_NAME, material.getName());
            values.put(KEY_PRICE_PER_KG, material.getPricePerKg());
            values.put(KEY_ICON, material.getIcon());
            values.put(KEY_DESCRIPTION, material.getDescription());
            values.put(KEY_CREATED_AT, material.getCreatedAt());
            values.put(KEY_UPDATED_AT, material.getUpdatedAt());
            values.put(KEY_IS_ACTIVE, 1);

            try {
                long id = db.insert(TABLE_MATERIALS, null, values);
                Log.d(TAG, "Inserted default material: " + material.getName() + " with ID: " + id);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting default material " + material.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Clear all materials (for testing purposes)
     */
    public void clearAllMaterials() {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.delete(TABLE_MATERIALS, null, null);
            Log.d(TAG, "All materials cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing materials: " + e.getMessage());
        }
    }
}