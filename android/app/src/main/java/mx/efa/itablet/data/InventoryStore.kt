package mx.efa.itablet.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class InventoryHeader(
    val id: Long, val responsible: String, val photoPath: String,
    val warehouse: String, val warehouseName: String,
    val startedAt: Long, val updatedAt: Long, val finishedAt: Long?,
    val status: String, val queryStartedAt: Long?, val queryFinishedAt: Long?
)

class InventoryStore(context: Context) : SQLiteOpenHelper(context, "itablet_inventory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE inventories(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            responsible TEXT NOT NULL, photo_path TEXT NOT NULL,
            warehouse TEXT NOT NULL, warehouse_name TEXT NOT NULL,
            started_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            finished_at INTEGER, status TEXT NOT NULL,
            query_started_at INTEGER, query_finished_at INTEGER)""")
        db.execSQL("""CREATE TABLE stock(
            inventory_id INTEGER NOT NULL, item_code TEXT NOT NULL,
            item_name TEXT NOT NULL, batch TEXT NOT NULL, sap_qty REAL NOT NULL,
            counted_qty REAL, pieces REAL, counted_at INTEGER,
            PRIMARY KEY(inventory_id,item_code,batch),
            FOREIGN KEY(inventory_id) REFERENCES inventories(id))""")
        db.execSQL("CREATE UNIQUE INDEX one_active_inventory ON inventories(status) WHERE status='ACTIVE'")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Se requiere una migración explícita de datos")
    }
    private fun header(c: android.database.Cursor): InventoryHeader = InventoryHeader(
        c.getLong(c.getColumnIndexOrThrow("id")),
        c.getString(c.getColumnIndexOrThrow("responsible")),
        c.getString(c.getColumnIndexOrThrow("photo_path")),
        c.getString(c.getColumnIndexOrThrow("warehouse")),
        c.getString(c.getColumnIndexOrThrow("warehouse_name")),
        c.getLong(c.getColumnIndexOrThrow("started_at")),
        c.getLong(c.getColumnIndexOrThrow("updated_at")),
        if (c.isNull(c.getColumnIndexOrThrow("finished_at"))) null else c.getLong(c.getColumnIndexOrThrow("finished_at")),
        c.getString(c.getColumnIndexOrThrow("status")),
        if (c.isNull(c.getColumnIndexOrThrow("query_started_at"))) null else c.getLong(c.getColumnIndexOrThrow("query_started_at")),
        if (c.isNull(c.getColumnIndexOrThrow("query_finished_at"))) null else c.getLong(c.getColumnIndexOrThrow("query_finished_at"))
    )
    fun active(): InventoryHeader? = readableDatabase.rawQuery(
        "SELECT * FROM inventories WHERE status='ACTIVE' LIMIT 1", null
    ).use { if (it.moveToFirst()) header(it) else null }

    fun history(): List<InventoryHeader> = readableDatabase.rawQuery(
        "SELECT * FROM inventories WHERE status='FINISHED' ORDER BY finished_at DESC", null
    ).use { c -> buildList { while (c.moveToNext()) add(header(c)) } }

    fun start(responsible: String, photoPath: String, warehouse: String, warehouseName: String): Long {
        require(responsible.isNotBlank() && photoPath.isNotBlank() && warehouse.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active() == null) { "Ya existe un inventario activo" }
            val now = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                put("responsible", responsible.trim()); put("photo_path", photoPath)
                put("warehouse", warehouse); put("warehouse_name", warehouseName)
                put("started_at", now); put("updated_at", now); put("status", "ACTIVE")
            }
            val id = db.insertOrThrow("inventories", null, values)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    fun saveSnapshot(id: Long, rows: List<JSONObject>, startedAt: Long, finishedAt: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active()?.id == id) { "Inventario no activo" }
            db.delete("stock", "inventory_id=? AND counted_at IS NULL", arrayOf(id.toString()))
            rows.forEach { row ->
                val values = android.content.ContentValues().apply {
                    put("inventory_id", id); put("item_code", row.getString("ItemCode"))
                    put("item_name", row.optString("ItemName"))
                    put("batch", row.getString("BatchNumber"))
                    put("sap_qty", row.getDouble("InStock"))
                }
                db.insertWithOnConflict("stock", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.execSQL("UPDATE inventories SET query_started_at=?,query_finished_at=?,updated_at=? WHERE id=?",
                arrayOf(startedAt, finishedAt, finishedAt, id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun count(id: Long, itemCode: String, batch: String, quantity: Double, pieces: Double) {
        require(quantity.isFinite() && pieces.isFinite() && quantity >= 0 && pieces >= 0)
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active()?.id == id) { "Inventario no activo" }
            val values = android.content.ContentValues().apply {
                put("counted_qty", quantity); put("pieces", pieces); put("counted_at", now)
            }
            check(db.update("stock", values, "inventory_id=? AND item_code=? AND batch=?",
                arrayOf(id.toString(), itemCode, batch)) == 1) { "Lote no encontrado" }
            db.execSQL("UPDATE inventories SET updated_at=? WHERE id=?", arrayOf(now,id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun rows(id: Long): List<JSONObject> = readableDatabase.rawQuery(
        "SELECT * FROM stock WHERE inventory_id=? ORDER BY item_code,batch", arrayOf(id.toString())
    ).use { c -> buildList {
        while (c.moveToNext()) add(JSONObject().apply {
            put("ItemCode", c.getString(c.getColumnIndexOrThrow("item_code")))
            put("ItemName", c.getString(c.getColumnIndexOrThrow("item_name")))
            put("BatchNumber", c.getString(c.getColumnIndexOrThrow("batch")))
            put("InStock", c.getDouble(c.getColumnIndexOrThrow("sap_qty")))
            val q = c.getColumnIndexOrThrow("counted_qty")
            val p = c.getColumnIndexOrThrow("pieces")
            if (!c.isNull(q)) put("CountedQty", c.getDouble(q))
            if (!c.isNull(p)) put("Pieces", c.getDouble(p))
        })
    } }
    fun finish(id: Long, allowIncomplete: Boolean = false) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(active()?.id == id) { "Inventario no activo" }
            check(rows(id).isNotEmpty()) { "No hay existencias descargadas" }
            val pending = db.rawQuery("SELECT COUNT(*) FROM stock WHERE inventory_id=? AND counted_at IS NULL", arrayOf(id.toString()))
                .use { it.moveToFirst(); it.getInt(0) }
            check(allowIncomplete || pending == 0) { "Faltan $pending artículos por contar" }
            val now = System.currentTimeMillis()
            db.execSQL("UPDATE inventories SET status='FINISHED',finished_at=?,updated_at=? WHERE id=?",
                arrayOf(now,now,id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
