/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.modeler.editor.dbimport;

import javax.swing.tree.TreePath;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.apache.cayenne.dba.DbAdapter;
import org.apache.cayenne.dba.sqlite.SQLiteAdapter;
import org.apache.cayenne.dbsync.reverse.dbimport.Catalog;
import org.apache.cayenne.dbsync.reverse.dbimport.FilterContainer;
import org.apache.cayenne.dbsync.reverse.dbimport.IncludeColumn;
import org.apache.cayenne.dbsync.reverse.dbimport.IncludeProcedure;
import org.apache.cayenne.dbsync.reverse.dbimport.IncludeTable;
import org.apache.cayenne.dbsync.reverse.dbimport.PatternParam;
import org.apache.cayenne.dbsync.reverse.dbimport.ReverseEngineering;
import org.apache.cayenne.dbsync.reverse.dbimport.Schema;
import org.apache.cayenne.modeler.ClassLoadingService;
import org.apache.cayenne.modeler.dialog.db.load.DbImportTreeNode;
import org.apache.cayenne.modeler.pref.DBConnectionInfo;

public class DatabaseSchemaLoader {

    private static final String INCLUDE_ALL_PATTERN = "%";

    private ReverseEngineering databaseReverseEngineering;

    public DatabaseSchemaLoader() {
        databaseReverseEngineering = new ReverseEngineering();
    }

    public ReverseEngineering load(DBConnectionInfo connectionInfo,
                                   ClassLoadingService loadingService) throws Exception {
        DbAdapter dbAdapter = connectionInfo.makeAdapter(loadingService);
        try (Connection connection = connectionInfo.makeDataSource(loadingService).getConnection()) {
            processCatalogs(connection, dbAdapter);
        }

        sort();
        return databaseReverseEngineering;
    }

    private void sort() {
        databaseReverseEngineering.getCatalogs().forEach(catalog -> {
            catalog.getSchemas().forEach(this::sort);
            sort(catalog);
        });
        sort(databaseReverseEngineering);
    }

    private void sort(FilterContainer filterContainer) {
        Comparator<PatternParam> comparator = Comparator.comparing(PatternParam::getPattern);
        filterContainer.getIncludeTables().sort(comparator);
        filterContainer.getIncludeTables().forEach(table -> table.getIncludeColumns().sort(comparator));
        filterContainer.getIncludeProcedures().sort(comparator);
    }

    private void processCatalogs(Connection connection, DbAdapter dbAdapter) throws SQLException {
        try (ResultSet rsCatalog = connection.getMetaData().getCatalogs()) {
            boolean hasCatalogs = false;
            List<String> systemCatalogs = dbAdapter.getSystemCatalogs();
            while (rsCatalog.next() && dbAdapter.supportsCatalogsOnReverseEngineering()) {
                hasCatalogs = true;
                String catalog = rsCatalog.getString("TABLE_CAT");
                if(!systemCatalogs.contains(catalog)) {
                    processSchemas(connection, catalog, dbAdapter);
                }
            }
            if(!hasCatalogs) {
                processSchemas(connection, null, dbAdapter);
            }
        }
    }

    private void processSchemas(Connection connection,
                                String catalog,
                                DbAdapter dbAdapter) throws SQLException {
        if (!dbAdapter.supportsSchemasOnReverseEngineering()) {
            packFilterContainer(null, SQLiteAdapter.MAIN_SCHEMA);
            return;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        try(ResultSet rsSchema = metaData.getSchemas(catalog, null)) {
            boolean hasSchemas = false;
            List<String> systemSchemas = dbAdapter.getSystemSchemas();
            while (rsSchema.next()) {
                hasSchemas = true;
                String schema = rsSchema.getString("TABLE_SCHEM");
                if(!systemSchemas.contains(schema)) {
                    packFilterContainer(catalog, schema);
                }
            }
            if(!hasSchemas) {
                packFilterContainer(catalog, null);
            }
        }
    }

    public ReverseEngineering loadTables(DBConnectionInfo connectionInfo,
                                         ClassLoadingService loadingService,
                                         TreePath path,
                                         String[] tableTypesFromConfig) throws SQLException {
        int pathIndex = 1;
        String catalogName = null, schemaName = null;

        Object userObject = getUserObject(path, pathIndex);
        if(userObject instanceof Catalog) {
            Catalog catalog = (Catalog) userObject;
            catalogName = catalog.getName();
            if(!catalog.getSchemas().isEmpty()) {
                userObject = getUserObject(path, ++pathIndex);
                if (userObject instanceof Schema) {
                    schemaName = ((Schema) userObject).getName();
                }
            }
        } else if(userObject instanceof Schema) {
            schemaName = ((Schema) userObject).getName();
        }

        try (Connection connection = connectionInfo.makeDataSource(loadingService).getConnection()) {
            String[] types = tableTypesFromConfig != null && tableTypesFromConfig.length != 0 ?
                    tableTypesFromConfig :
                    new String[]{"TABLE", "VIEW", "SYSTEM TABLE",
                            "GLOBAL TEMPORARY", "LOCAL TEMPORARY", "ALIAS", "SYNONYM"};
            try (ResultSet resultSet = connection.getMetaData()
                    .getTables(catalogName, schemaName, INCLUDE_ALL_PATTERN, types)) {
                boolean hasTables = false;
                while (resultSet.next()) {
                    hasTables = true;
                    String table = resultSet.getString("TABLE_NAME");
                    String schema = resultSet.getString("TABLE_SCHEM");
                    String catalog = resultSet.getString("TABLE_CAT");
                    if (connectionInfo.getDbAdapter().equals(SQLiteAdapter.class.getName()) && schema == null && schemaName != null) {
                        schema = schemaName;
                    }

                    packTable(table, catalog == null ? catalogName : catalog, schema, null);
                }
                if(!hasTables) {
                    packFilterContainer(catalogName, schemaName);
                }
                packProcedures(connection);
            }
        }
        return databaseReverseEngineering;
    }

    public ReverseEngineering loadColumns(DBConnectionInfo connectionInfo,
                                          ClassLoadingService loadingService,
                                          TreePath path) throws SQLException {
        int pathIndex = 1;
        String catalogName = null, schemaName = null;

        Object userObject = getUserObject(path, pathIndex);
        if(userObject instanceof Catalog) {
            catalogName = ((Catalog) userObject).getName();
            userObject = getUserObject(path, ++pathIndex);
            if(userObject instanceof Schema) {
                schemaName = ((Schema) userObject).getName();
                userObject = getUserObject(path, ++pathIndex);
            }
        } else if(userObject instanceof Schema) {
            schemaName = ((Schema) userObject).getName();
            userObject = getUserObject(path, ++pathIndex);
        }

        String tableName = processTable(userObject);
        try (Connection connection = connectionInfo.makeDataSource(loadingService).getConnection()) {
            try (ResultSet rs = connection.getMetaData().getColumns(catalogName, schemaName, tableName, null)) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    packTable(tableName, catalogName, schemaName, column);
                }
            }
        }
        sort();
        return databaseReverseEngineering;
    }

    private FilterContainer packFilterContainer(String catalogName, String schemaName) {
        if (catalogName != null && schemaName == null) {
            Catalog parentCatalog = getCatalogByName(databaseReverseEngineering.getCatalogs(), catalogName);

            if(parentCatalog == null) {
                parentCatalog = new Catalog();
                parentCatalog.setName(catalogName);
                databaseReverseEngineering.addCatalog(parentCatalog);
            }

            return parentCatalog;
        } else if (catalogName == null) {
            Schema parentSchema = getSchemaByName(databaseReverseEngineering.getSchemas(), schemaName);

            if(parentSchema == null) {
                parentSchema = new Schema();
                parentSchema.setName(schemaName);
                databaseReverseEngineering.addSchema(parentSchema);
            }
            return parentSchema;
        } else {
            Catalog parentCatalog = getCatalogByName(databaseReverseEngineering.getCatalogs(), catalogName);
            Schema parentSchema;
            if (parentCatalog != null) {
                parentSchema = getSchemaByName(parentCatalog.getSchemas(), schemaName);
                if(parentSchema == null) {
                    parentSchema = new Schema();
                    parentSchema.setName(schemaName);
                    parentCatalog.addSchema(parentSchema);
                }
            } else {
                parentCatalog = new Catalog();
                parentCatalog.setName(catalogName);
                parentSchema = new Schema();
                parentSchema.setName(schemaName);
                parentCatalog.addSchema(parentSchema);
                databaseReverseEngineering.addCatalog(parentCatalog);
            }
            return parentSchema;
        }
    }

    private Object getUserObject(TreePath path, int pathIndex) {
        return ((DbImportTreeNode)path.getPathComponent(pathIndex)).getUserObject();
    }

    private String processTable(Object userObject) {
        if(userObject instanceof IncludeTable) {
            return  ((IncludeTable) userObject).getPattern();
        }
        return null;
    }

    private void packProcedures(Connection connection) throws SQLException {
        Collection<Catalog> catalogs = databaseReverseEngineering.getCatalogs();
        for(Catalog catalog : catalogs) {
            Collection<Schema> schemas = catalog.getSchemas();
            if(!schemas.isEmpty()) {
                for(Schema schema : schemas) {
                    ResultSet procResultSet = getProcedures(connection, catalog.getName(), schema.getName());
                    packProcedures(procResultSet, schema);
                }
            } else {
                ResultSet procResultSet = getProcedures(connection, catalog.getName(), null);
                packProcedures(procResultSet, catalog);
            }
        }

        Collection<Schema> schemas = databaseReverseEngineering.getSchemas();
        for(Schema schema : schemas) {
            ResultSet procResultSet = getProcedures(connection, null, schema.getName());
            packProcedures(procResultSet, schema);
        }
    }

    private ResultSet getProcedures(Connection connection, String catalog, String schema) throws SQLException {
        return connection.getMetaData().getProcedures(catalog, schema, "%");
    }

    private void packProcedures(ResultSet resultSet, FilterContainer filterContainer) throws SQLException {
        while (resultSet.next()) {
            IncludeProcedure includeProcedure =
                    new IncludeProcedure(resultSet.getString("PROCEDURE_NAME"));
            if (!filterContainer.getIncludeProcedures().contains(includeProcedure)) {
                filterContainer.addIncludeProcedure(includeProcedure);
            }
        }
    }

    private void packTable(String tableName, String catalogName, String schemaName, String columnName) {
        IncludeTable table = new IncludeTable();
        table.setPattern(tableName);

        if (catalogName == null && schemaName == null) {
            if (!databaseReverseEngineering.getIncludeTables().contains(table)) {
                databaseReverseEngineering.addIncludeTable(table);
            }
            return;
        }

        FilterContainer filterContainer = packFilterContainer(catalogName, schemaName);
        addTable(filterContainer, table);
        addColumn(filterContainer, table, columnName);
    }

    private void addTable(FilterContainer parentFilter, IncludeTable table) {
        if (!parentFilter.getIncludeTables().contains(table)) {
            parentFilter.addIncludeTable(table);
        }
    }

    private void addColumn(FilterContainer filterContainer, IncludeTable table, String columnName) {
        IncludeTable foundTable = getTableByName(filterContainer.getIncludeTables(), table.getPattern());
        table = foundTable != null ? foundTable : table;
        if (columnName != null) {
            IncludeColumn includeColumn = new IncludeColumn(columnName);
            table.addIncludeColumn(includeColumn);
        }
    }

    private Catalog getCatalogByName(Collection<Catalog> catalogs, String catalogName) {
        for (Catalog catalog : catalogs) {
            if (catalog.getName().equals(catalogName)) {
                return catalog;
            }
        }
        return null;
    }

    private IncludeTable getTableByName(Collection<IncludeTable> tables, String catalogName) {
        for (IncludeTable table : tables) {
            if (table.getPattern().equals(catalogName)) {
                return table;
            }
        }
        return null;
    }

    private Schema getSchemaByName(Collection<Schema> schemas, String schemaName) {
        for (Schema schema : schemas) {
            if (schema.getName().equals(schemaName)) {
                return schema;
            }
        }
        return null;
    }
}
