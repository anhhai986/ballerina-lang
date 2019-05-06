/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.jvm;

import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BField;
import org.ballerinalang.jvm.types.BStructureType;
import org.ballerinalang.jvm.types.BType;
import org.ballerinalang.jvm.types.TypeTags;
import org.ballerinalang.jvm.util.exceptions.BallerinaException;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.jvm.values.RefValue;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

/**
 * {@code TableProvider} creates In Memory database for tables.
 *
 * @since 0.995.0
 */
public class TableProvider {

    private static TableProvider tableProvider = null;
    private int tableID;
    private int indexID;

    private TableProvider() {
        tableID = 0;
        indexID = 0;
    }

    public static TableProvider getInstance() {
        if (tableProvider != null) {
            return tableProvider;
        }
        synchronized (TableProvider.class) {
            if (tableProvider == null) {
                tableProvider = new TableProvider();
            }
        }
        return tableProvider;
    }

    private synchronized int getTableID() {
        return this.tableID++;
    }

    private synchronized int getIndexID() {
        return this.indexID++;
    }

    public String createTable(BType constrainedType, ArrayValue primaryKeys, ArrayValue indexeColumns) {
        String tableName = TableConstants.TABLE_PREFIX + constrainedType.getName()
                .toUpperCase() + "_" + getTableID();
        String sqlStmt = generateCreateTableStatment(tableName, constrainedType, primaryKeys);
        executeStatement(sqlStmt);

        //Add Index Data
        if (indexeColumns != null) {
            generateIndexesForTable(tableName, indexeColumns);
        }
        return tableName;
    }

    public String createTable(String fromTableName, String joinTableName,  String query, BStructureType tableType,
                              ArrayValue params) {
        String newTableName = TableConstants.TABLE_PREFIX + tableType.getName().toUpperCase()
                              + "_" + getTableID();
        String sqlStmt = query.replaceFirst(TableConstants.TABLE_NAME_REGEX, fromTableName);
        if (joinTableName != null && !joinTableName.isEmpty()) {
            sqlStmt = sqlStmt.replaceFirst(TableConstants.TABLE_NAME_REGEX, joinTableName);
        }
        sqlStmt = generateCreateTableStatment(sqlStmt, newTableName);
        prepareAndExecuteStatement(sqlStmt, params);
        return newTableName;
    }

    public String createTable(String fromTableName, String query, BStructureType tableType,
                              ArrayValue params) {
        return createTable(fromTableName, null, query, tableType, params);
    }

    public void insertData(String tableName, MapValue<String, Object> constrainedType) {
        String sqlStmt = TableUtils.generateInsertDataStatment(tableName, constrainedType);
        prepareAndExecuteStatement(sqlStmt, constrainedType);
    }

    public void deleteData(String tableName, MapValue<String, Object> constrainedType) {
        String sqlStmt = TableUtils.generateDeleteDataStatment(tableName, constrainedType);
        prepareAndExecuteStatement(sqlStmt, constrainedType);
    }

    public void dropTable(String tableName) {
        String sqlStmt = TableConstants.SQL_DROP + tableName;
        executeStatement(sqlStmt);
    }

    public TableIterator createIterator(String tableName, BStructureType type) {
        TableIterator itr;
        Statement stmt = null;
        Connection conn = this.getConnection();
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(TableConstants.SQL_SELECT + tableName);
            TableResourceManager rm = new TableResourceManager(conn, stmt, true);
            rm.addResultSet(rs);
            itr = new TableIterator(rm, rs, type);
        } catch (SQLException e) {
            releaseResources(conn, stmt);
            throw new BallerinaException("error in creating iterator for table : " + e.getMessage());
        }
        return itr;
    }

    private Connection getConnection() {
        Connection conn;
        try {
            conn = DriverManager
                    .getConnection(TableConstants.DB_JDBC_URL, TableConstants.DB_USER_NAME, TableConstants.DB_PASSWORD);
        } catch (SQLException e) {
            throw new BallerinaException("error in gettign connection for table db : " + e.getMessage());
        }
        return conn;
    }

    private String generateCreateTableStatment(String tableName, BType constrainedType, ArrayValue primaryKeys) {
        StringBuilder sb = new StringBuilder();
        sb.append(TableConstants.SQL_CREATE).append(tableName).append(" (");
        Collection<BField> structFields = ((BStructureType) constrainedType).getFields().values();
        String seperator = "";
        for (BField sf : structFields) {
            int type = sf.getFieldType().getTag();
            String name = sf.getFieldName();
            sb.append(seperator).append(name).append(" ");
            switch (type) {
                case TypeTags.INT_TAG:
                    sb.append(TableConstants.SQL_TYPE_BIGINT);
                    break;
                case TypeTags.STRING_TAG:
                    sb.append(TableConstants.SQL_TYPE_VARCHAR);
                    break;
                case TypeTags.FLOAT_TAG:
                case TypeTags.DECIMAL_TAG:
                    sb.append(TableConstants.SQL_TYPE_DOUBLE);
                    break;
                case TypeTags.BOOLEAN_TAG:
                    sb.append(TableConstants.SQL_TYPE_BOOLEAN);
                    break;
                case TypeTags.JSON_TAG:
                case TypeTags.XML_TAG:
                    sb.append(TableConstants.SQL_TYPE_CLOB);
                    break;
                case TypeTags.ARRAY_TAG:
                    BType elementType = ((BArrayType) sf.getFieldType()).getElementType();
                    if (elementType.getTag() == TypeTags.BYTE_TAG) {
                        sb.append(TableConstants.SQL_TYPE_BLOB);
                    } else {
                        sb.append(TableConstants.SQL_TYPE_ARRAY);
                    }
                    break;
                default:
                    throw new BallerinaException("Unsupported column type for table : " + sf.getFieldType());
            }
            seperator = ",";
        }
        //Add primary key information
        if (primaryKeys != null) {
            seperator = "";
            int primaryKeyCount = (int) primaryKeys.size();
            if (primaryKeyCount > 0) {
                sb.append(TableConstants.PRIMARY_KEY);
                for (int i = 0; i < primaryKeyCount; i++) {
                    sb.append(seperator).append(primaryKeys.getString(i));
                    seperator = ",";
                }
                sb.append(")");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private String generateCreateTableStatment(String query, String newTableName) {
        StringBuilder sb = new StringBuilder();
        sb.append(TableConstants.SQL_CREATE).append(newTableName).append(" ").append(TableConstants.SQL_AS);
        sb.append(query);
        return sb.toString();
    }

    private void generateIndexesForTable(String tableName, ArrayValue indexColumns) {
        int indexCount = indexColumns.size();
        if (indexCount > 0) {
            for (int i = 0; i < indexCount; i++) {
                StringBuilder sb = new StringBuilder();
                String columnName = indexColumns.getString(i);
                sb.append(TableConstants.SQL_CREATE_INDEX).append(TableConstants.INDEX).append(columnName)
                        .append(getIndexID()).append(TableConstants.SQL_ON).append(tableName).append("(")
                        .append(columnName).append(")");
                executeStatement(sb.toString());
            }
        }
    }

    private void executeStatement(String queryStatement) {
        Statement stmt = null;
        Connection conn = this.getConnection();
        try {
            stmt = conn.createStatement();
            stmt.executeUpdate(queryStatement);
        } catch (SQLException e) {
            throw new BallerinaException (
                    "error in executing statement : " + queryStatement + " error:" + e.getMessage());
        } finally {
            releaseResources(conn, stmt);
        }
    }

    private void prepareAndExecuteStatement(String queryStatement, ArrayValue params) {
        PreparedStatement stmt = null;
        Connection conn = this.getConnection();
        try {
            stmt = conn.prepareStatement(queryStatement);
            for (int index = 1; index <= params.size(); index++) {
                RefValue param = (RefValue) params.getRefValue(index - 1);
                switch (param.getType().getTag()) {
                    case TypeTags.INT_TAG:
                        stmt.setLong(index, params.getInt(index - 1));
                        break;
                    case TypeTags.STRING_TAG:
                        stmt.setString(index, params.getString(index - 1));
                        break;
                    case TypeTags.FLOAT_TAG:
                        stmt.setDouble(index, params.getFloat(index - 1));
                        break;
                    case TypeTags.BOOLEAN_TAG:
                        stmt.setBoolean(index, params.getBoolean(index - 1));
                        break;
                    case TypeTags.XML_TAG:
                    case TypeTags.JSON_TAG:
                        stmt.setString(index, params.getString(index - 1));
                        break;
                    case TypeTags.ARRAY_TAG:
                        BType elementType = ((BArrayType) param.getType()).getElementType();
                        if (elementType.getTag() == TypeTags.BYTE_TAG) {
                            byte[] blobData = params.getBytes();
                            stmt.setBlob(index, new ByteArrayInputStream(blobData), blobData.length);
                        } else {
                            Object[] arrayData = TableUtils.getArrayData((ArrayValue) param);
                            stmt.setObject(index, arrayData);
                        }
                        break;
                }
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new BallerinaException(
                    "error in executing statement : " + queryStatement + " error:" + e.getMessage());
        } finally {
            releaseResources(conn, stmt);
        }
    }

    private void prepareAndExecuteStatement(String queryStatement, MapValue<String, Object> constrainedType) {
        PreparedStatement stmt = null;
        Connection conn = this.getConnection();
        try {
            stmt = conn.prepareStatement(queryStatement);
            TableUtils.prepareAndExecuteStatement(stmt, constrainedType);
        } catch (SQLException e) {
            throw new BallerinaException(
                    "error in executing statement : " + queryStatement + " error:" + e.getMessage());
        } finally {
            releaseResources(conn, stmt);
        }
    }

    private void releaseResources(Connection conn, Statement stmt) {
        try {
            if (stmt != null) {
                stmt.close();
            }
        } catch (SQLException e) {
            throw new BallerinaException("error in releasing table statement resource : " + e.getMessage());
        }
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new BallerinaException("error in releasing table connection resource : " + e.getMessage());
        }
    }

    public int getRowCount(String tableName) {
        Statement stmt = null;
        Connection conn = this.getConnection();
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(TableConstants.SQL_COUNT + tableName);
            int rowCount = 0;
            if (rs.next()) {
                rowCount = rs.getInt(1);
            }
            return rowCount;
        } catch (SQLException e) {
            throw new BallerinaException("error in executing statement to get the count : " + stmt + " error:"
                                         + e.getMessage());
        } finally {
            releaseResources(conn, stmt);
        }
    }
}