import { Client } from 'pg';
import * as fs from 'fs';

interface Config {
  connectionString: string;
  tableName: string;
  fileName: string;
}

const config: Config = {
  connectionString: 'postgres://user:password@localhost:5432/your_database',
  tableName: 'your_table_name',
  fileName: 'your_json_file.json'
};

const loadJSONToPostgres = async ({ connectionString, tableName, fileName }: Config) => {
  const client = new Client({
    connectionString
  });

  try {
    await client.connect();
    console.log('Connected to the database.');

    const jsonData = JSON.parse(fs.readFileSync(fileName, 'utf8'));
    const keys = Object.keys(jsonData[0]);

    // Create table with columns from JSON keys
    const createTableQuery = `
      CREATE TABLE IF NOT EXISTS ${tableName} (
        id SERIAL PRIMARY KEY,
        ${keys.map(key => `${key} TEXT`).join(', ')}
      );
    `;
    await client.query(createTableQuery);

    // Insert JSON data into the table
    for (const item of jsonData) {
      const columns = keys.join(', ');
      const values = keys.map(key => `'${item[key]}'`).join(', ');
      const insertQuery = `INSERT INTO ${tableName} (${columns}) VALUES (${values});`;
      await client.query(insertQuery);
    }
    console.log('Data successfully imported.');

  } catch (error) {
    console.error('Error:', error.message);
  } finally {
    await client.end();
    console.log('Disconnected from the database.');
  }
};

loadJSONToPostgres(config);
