PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS users (
 id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE COLLATE NOCASE,
 name TEXT NOT NULL, password TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN ('platform','merchant','customer')),
 store_id INTEGER REFERENCES stores(id), customer_id INTEGER REFERENCES customers(id), active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS stores (
 id INTEGER PRIMARY KEY, name TEXT NOT NULL, business TEXT NOT NULL,
 address TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS customers (
 id INTEGER PRIMARY KEY, store_id INTEGER NOT NULL REFERENCES stores(id),
 name TEXT NOT NULL, document TEXT NOT NULL, email TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '',
 currency TEXT NOT NULL CHECK(currency IN ('PEN','USD')), credit_limit TEXT NOT NULL,
 rate TEXT NOT NULL, rate_kind TEXT NOT NULL, rate_period INTEGER NOT NULL, frequency INTEGER NOT NULL,
 late_rate TEXT NOT NULL, late_kind TEXT NOT NULL, late_period INTEGER NOT NULL, late_frequency INTEGER NOT NULL,
 max_months INTEGER NOT NULL, cutoff_day INTEGER NOT NULL, cutoff_hour INTEGER NOT NULL,
 pay_day INTEGER NOT NULL, consent_at TEXT NOT NULL, consent_version TEXT NOT NULL DEFAULT '1',
 active INTEGER NOT NULL DEFAULT 1, UNIQUE(store_id,document)
);
CREATE TABLE IF NOT EXISTS products (
 id INTEGER PRIMARY KEY, store_id INTEGER NOT NULL REFERENCES stores(id),
 name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', brand TEXT NOT NULL DEFAULT '',
 supplier TEXT NOT NULL DEFAULT '', unit TEXT NOT NULL DEFAULT 'unidad', image TEXT NOT NULL DEFAULT '',
 cash_price TEXT NOT NULL, credit_price TEXT NOT NULL,
 currency TEXT NOT NULL CHECK(currency IN ('PEN','USD')),
 installment_allowed INTEGER NOT NULL DEFAULT 1, active INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS sales (
 id INTEGER PRIMARY KEY, store_id INTEGER NOT NULL REFERENCES stores(id),
 customer_id INTEGER NOT NULL REFERENCES customers(id), purchased_at TEXT NOT NULL,
 mode TEXT NOT NULL, months INTEGER NOT NULL, amount TEXT NOT NULL,
 financed TEXT NOT NULL, grace_days INTEGER NOT NULL, grace_interest TEXT NOT NULL,
 currency TEXT NOT NULL, terms_json TEXT NOT NULL, cycle TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sale_items (
 id INTEGER PRIMARY KEY, sale_id INTEGER NOT NULL REFERENCES sales(id),
 product_id INTEGER NOT NULL REFERENCES products(id), name TEXT NOT NULL,
 quantity TEXT NOT NULL, unit_price TEXT NOT NULL, subtotal TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS dues (
 id INTEGER PRIMARY KEY, sale_id INTEGER NOT NULL REFERENCES sales(id), number INTEGER NOT NULL,
 due_date TEXT NOT NULL, principal TEXT NOT NULL, interest TEXT NOT NULL,
 amount TEXT NOT NULL, payment_id INTEGER REFERENCES payments(id)
);
CREATE TABLE IF NOT EXISTS payments (
 id INTEGER PRIMARY KEY, store_id INTEGER NOT NULL REFERENCES stores(id),
 customer_id INTEGER NOT NULL REFERENCES customers(id), paid_at TEXT NOT NULL,
 base_amount TEXT NOT NULL, late_amount TEXT NOT NULL, amount TEXT NOT NULL, currency TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS payment_lines (
 id INTEGER PRIMARY KEY, payment_id INTEGER NOT NULL REFERENCES payments(id),
 due_id INTEGER NOT NULL REFERENCES dues(id), late_days INTEGER NOT NULL, late_amount TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
 token TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
 csrf TEXT NOT NULL, expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS challenges (
 id TEXT PRIMARY KEY, answer TEXT NOT NULL, expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS login_attempts (
 key TEXT PRIMARY KEY, failures INTEGER NOT NULL, blocked_until INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS audit (
 id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), store_id INTEGER,
 action TEXT NOT NULL, entity_id INTEGER, detail TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS customers_store ON customers(store_id);
CREATE INDEX IF NOT EXISTS products_store ON products(store_id);
CREATE INDEX IF NOT EXISTS sales_customer ON sales(customer_id,store_id);
CREATE INDEX IF NOT EXISTS dues_dates ON dues(due_date,payment_id);

CREATE TABLE IF NOT EXISTS statements (
 id INTEGER PRIMARY KEY, store_id INTEGER NOT NULL REFERENCES stores(id),
 customer_id INTEGER NOT NULL REFERENCES customers(id), cycle TEXT NOT NULL,
 snapshot TEXT NOT NULL, generated_at TEXT NOT NULL, UNIQUE(customer_id,cycle)
);
