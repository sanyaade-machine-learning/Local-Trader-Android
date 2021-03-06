/*
 * Copyright (c) 2018 ThanksMister LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.thanksmister.bitcoin.localtrader.data.database;

import android.content.ContentValues;
import android.database.Cursor;

import com.thanksmister.bitcoin.localtrader.network.api.model.Method;

import java.util.ArrayList;
import java.util.List;

import auto.parcel.AutoParcel;
import rx.functions.Func1;

import static com.squareup.sqlbrite.SqlBrite.Query;

@AutoParcel
public abstract class MethodItem {
    public static final String TABLE = "method_item";

    public static final String ID = "_id";
    public static final String KEY = "key";
    public static final String CODE = "code";
    public static final String NAME = "name";
    public static final String COUNTRY_CODE = "countryCode";
    public static final String COUNTRY_NAME = "countryName";

    public static final String QUERY = "SELECT * FROM "
            + MethodItem.TABLE
            + " ORDER BY "
            + MethodItem.KEY
            + " ASC";

    public abstract long id();

    public abstract String key();

    public abstract String code();

    public abstract String name();

    public abstract String countryCode();

    public abstract String countryName();

    public static MethodItem getModel(Cursor cursor) {
        long id = Db.getLong(cursor, ID);
        String key = Db.getString(cursor, KEY);
        String code = Db.getString(cursor, CODE);
        String name = Db.getString(cursor, NAME);
        String countryCode = Db.getString(cursor, COUNTRY_CODE);
        String countryName = Db.getString(cursor, COUNTRY_NAME);
        return new AutoParcel_MethodItem(id, key, code, name, countryCode, countryName);
    }

    ;

    public static MethodItem getModelItem(Method method) {
        return new AutoParcel_MethodItem(0, method.key, method.code, method.name, method.countryCode, method.countryName);
    }

    public static List<MethodItem> getModelList(Cursor cursor) {
        List<MethodItem> values = new ArrayList<>(cursor.getCount());
        try {
            while (cursor.moveToNext()) {
                long id = Db.getLong(cursor, ID);
                String key = Db.getString(cursor, KEY);
                String code = Db.getString(cursor, CODE);
                String name = Db.getString(cursor, NAME);
                String countryCode = Db.getString(cursor, COUNTRY_CODE);
                String countryName = Db.getString(cursor, COUNTRY_NAME);
                values.add(new AutoParcel_MethodItem(id, key, code, name, countryCode, countryName));
            }
            return values;
        } finally {
            cursor.close();
        }
    }

    public static final Func1<Query, List<MethodItem>> MAP = new Func1<Query, List<MethodItem>>() {
        @Override
        public List<MethodItem> call(Query query) {
            Cursor cursor = query.run();
            List<MethodItem> values = new ArrayList<>(cursor.getCount());
            try {
                while (cursor.moveToNext()) {
                    long id = Db.getLong(cursor, ID);
                    String key = Db.getString(cursor, KEY);
                    String code = Db.getString(cursor, CODE);
                    String name = Db.getString(cursor, NAME);
                    String countryCode = Db.getString(cursor, COUNTRY_CODE);
                    String countryName = Db.getString(cursor, COUNTRY_NAME);
                    values.add(new AutoParcel_MethodItem(id, key, code, name, countryCode, countryName));
                }
                return values;
            } finally {
                cursor.close();
            }
        }
    };

    public static final Func1<Query, List<MethodItem>> MAP_SUBSET = new Func1<Query, List<MethodItem>>() {
        @Override
        public List<MethodItem> call(Query query) {
            Cursor cursor = query.run();
            List<MethodItem> values = new ArrayList<>(cursor.getCount());
            try {
                while (cursor.moveToNext()) {
                    long id = Db.getLong(cursor, ID);
                    String key = Db.getString(cursor, KEY);
                    String code = Db.getString(cursor, CODE);
                    String name = Db.getString(cursor, NAME);
                    String countryCode = Db.getString(cursor, COUNTRY_CODE);
                    String countryName = Db.getString(cursor, COUNTRY_NAME);

                    if (!code.toUpperCase().equals("ALL")) {
                        values.add(new AutoParcel_MethodItem(id, key, code, name, countryCode, countryName));
                    }
                }
                return values;
            } finally {
                cursor.close();
            }
        }
    };

    public static MethodItem convertMethod(Method method) {
        return new AutoParcel_MethodItem(0, method.key, method.code, method.name, method.countryCode, method.countryName);
    }

    ;

    public static ContentValues getContentValues(Method item, long id) {
        ContentValues values = createBuilder(item).build();
        values.put(ID, id);
        return values;
    }

    public static Builder createBuilder(Method item) {
        return new Builder()
                .key(item.key)
                .name(item.name)
                .code(item.code)
                .countryCode(item.countryCode)
                .countryName(item.countryName);
    }

    public static final class Builder {
        private final ContentValues values = new ContentValues();

        public Builder id(long id) {
            values.put(ID, id);
            return this;
        }

        public Builder key(String value) {
            values.put(KEY, value);
            return this;
        }

        public Builder code(String value) {
            values.put(CODE, value);
            return this;
        }

        public Builder name(String value) {
            values.put(NAME, value);
            return this;
        }

        public Builder countryName(String value) {
            values.put(COUNTRY_NAME, value);
            return this;
        }

        public Builder countryCode(String value) {
            values.put(COUNTRY_CODE, value);
            return this;
        }

        public ContentValues build() {
            return values;
        }
    }
}