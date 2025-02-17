Certainly! Here's the full code for the rule engine wrapper library, the dynamic facts, the rules JSON, and the Lambda handler, all following the SOLID principles and clean code practices.

### Step 1: Creating the Rule Engine Wrapper Library

1. **Install necessary dependencies**:
   ```bash
   npm install json-rules-engine aws-sdk pg
   ```

2. **Creating Service Classes**
   - **FactService**: Fetches fact data from RDS.
   - **RuleService**: Manages rules and their evaluation.
   - **S3Service**: Fetches rules from S3.

```typescript
// services/FactService.ts
import { Client } from 'pg';

class FactService {
  private client: Client;

  constructor() {
    this.client = new Client({
      host: process.env.RDS_HOST,
      port: parseInt(process.env.RDS_PORT, 10),
      user: process.env.RDS_USER,
      password: process.env.RDS_PASSWORD,
      database: process.env.RDS_DATABASE,
    });
  }

  async fetchFactData(query: string): Promise<any> {
    await this.client.connect();
    const res = await this.client.query(query);
    await this.client.end();
    return res.rows;
  }
}

export default FactService;
```

```typescript
// services/RuleService.ts
import { Engine, Rule } from 'json-rules-engine';

class RuleService {
  private engine: Engine;

  constructor() {
    this.engine = new Engine();
  }

  addRules(rules: any[]): void {
    for (const rule of rules) {
      this.engine.addRule(new Rule(rule));
    }
  }

  async evaluateRules(dynamicFacts: { [key: string]: (params: any, almanac: any) => Promise<any> }): Promise<any> {
    for (const [factId, factFn] of Object.entries(dynamicFacts)) {
      this.engine.addFact(factId, factFn);
    }

    const results = await this.engine.run({});
    return results;
  }
}

export default RuleService;
```

```typescript
// services/S3Service.ts
import { S3 } from 'aws-sdk';

class S3Service {
  private s3: S3;

  constructor() {
    this.s3 = new S3();
  }

  async fetchRulesFromS3(bucket: string, key: string): Promise<any> {
    const params = { Bucket: bucket, Key: key };
    const data = await this.s3.getObject(params).promise();
    return JSON.parse(data.Body.toString('utf-8'));
  }
}

export default S3Service;
```

3. **Creating the Wrapper Library (`src/ruleEngineWrapper.ts`)**

```typescript
import FactService from './services/FactService';
import RuleService from './services/RuleService';
import S3Service from './services/S3Service';
import { DynamicFact } from './types';

class RuleEngineWrapper {
  private factService: FactService;
  private ruleService: RuleService;
  private s3Service: S3Service;

  constructor() {
    this.factService = new FactService();
    this.ruleService = new RuleService();
    this.s3Service = new S3Service();
  }

  async fetchFactData(query: string): Promise<any> {
    return this.factService.fetchFactData(query);
  }

  async evaluateRules(rules: any[], dynamicFacts: { [key: string]: DynamicFact }): Promise<any> {
    this.ruleService.addRules(rules);
    return this.ruleService.evaluateRules(dynamicFacts);
  }

  async fetchRulesFromS3(bucket: string, key: string): Promise<any> {
    return this.s3Service.fetchRulesFromS3(bucket, key);
  }
}

export default RuleEngineWrapper;
```

### Step 2: Creating the Lambda Function

1. **Create the Lambda Handler (`src/index.ts`)**

```typescript
import RuleEngineWrapper from 'rule-engine-wrapper';
import { S3 } from 'aws-sdk';
import { DynamicFact } from './types';

const s3 = new S3();
const ruleEngineWrapper = new RuleEngineWrapper();

export const handler = async (event: any): Promise<any> => {
  const { bucket, key, dynamicFacts } = event;

  // Fetch rules from S3
  const rules = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);

  // Evaluate rules with dynamic facts
  const results = await ruleEngineWrapper.evaluateRules(rules, dynamicFacts);

  return {
    statusCode: 200,
    body: JSON.stringify(results),
  };
};
```

### Step 3: Implementing Specific Rules and Dynamic Facts with Direct Date Checks

1. **Dynamic Facts and Rules** (Specific to the Lambda function)

**Rules JSON**:
```json
[
  {
    "conditions": {
      "all": [
        {
          "fact": "nextTradingDate",
          "operator": "greaterThanInclusive",
          "value": "$today",
          "path": "$.trade_date"
        }
      ]
    },
    "event": {
      "type": "nextTradingDate",
      "params": {
        "message": "Next trading date found"
      }
    },
    "priority": 10 // Higher priority to run first
  },
  {
    "conditions": {
      "all": [
        {
          "fact": "eligibleInstruments",
          "operator": "greaterThanInclusive",
          "value": "$nextTradingDate",
          "path": "$.first_trading_date"
        },
        {
          "fact": "eligibleInstruments",
          "operator": "lessThanInclusive",
          "value": "$nextTradingDate",
          "path": "$.last_trading_date"
        },
        {
          "fact": "applicableContracts",
          "operator": "contains",
          "value": {
            "commodity_code": "$commodityCode",
            "instrument_type": "$instrumentType"
          }
        },
        {
          "fact": "tradeTypeMapping",
          "operator": "contains",
          "value": {
            "trade_type": ["block", "efp"],
            "commodity_code": "$commodityCode",
            "instrument_type": "$instrumentType"
          }
        }
      ]
    },
    "event": {
      "type": "eligibleInstrument",
      "params": {
        "message": "Instrument is eligible"
      }
    },
    "priority": 5 // Lower priority to run after the first rule
  }
]
```

**Dynamic Facts Implementation**:
```typescript
import RuleEngineWrapper from 'rule-engine-wrapper';
import { DynamicFact } from './types';

// Initialize Rule Engine Wrapper
const ruleEngineWrapper = new RuleEngineWrapper();

// Fact Function for Next Trading Date
const getNextTradingDate: DynamicFact = async (params, almanac) => {
  const query = "SELECT trade_date FROM calendar WHERE trade_date_indicator = 'Y' AND trade_date > NOW() ORDER BY trade_date LIMIT 1";
  const data = await ruleEngineWrapper.fetchFactData(query);
  return data[0];
};

// Fact Function for Eligible Instruments
const getEligibleInstruments: DynamicFact = async (params, almanac) => {
  const query = `
    SELECT 
      i.instrument_code,
      i.commodity_code,
      i.instrument_type,
      i.first_trading_date,
      i.last_trading_date,
      ac.commodity_code as applicable_commodity_code,
      ac.instrument_type as applicable_instrument_type,
      ttm.trade_type,
      ttm.commodity_code as trade_commodity_code,
      ttm.instrument_type as trade_instrument_type
    FROM instruments i
    JOIN applicable_contracts ac ON i.commodity_code = ac.commodity_code AND i.instrument_type = ac.instrument_type
    JOIN trade_type_mapping ttm ON i.commodity_code = ttm.commodity_code AND i.instrument_type = ttm.instrument_type
  `;
  const data = await ruleEngineWrapper.fetchFactData(query);
  return data;
};

// Define Dynamic Facts
const dynamicFacts: { [key: string]: DynamicFact } = {
  "nextTradingDate": getNextTradingDate,
  "eligibleInstruments": getEligibleInstruments
};
```

### Step 4: Defining the Types

**Create a file named `types.ts` to define the types:**
```typescript
// types.ts

// Type definition for DynamicFact
export type DynamicFact = (params: any, almanac: any) => Promise<any>;
```

### Step 5: Deploy the Lambda Function

1. **Bundle and Deploy**

```bash
npm run build
zip -r lambda-package.zip .
aws lambda update-function-code --function-name YourLambdaFunction --zip-file fileb://lambda-package.zip
```

### Summary

This solution includes a type definition for `DynamicFact`, ensuring type safety and better maintainability in TypeScript. The rules and dynamic facts are updated accordingly, making the code cleaner and more organized. The rules JSON now checks the `first_trading_date`, `last_trading_date`, and `trade_type` directly within the rules, making them self-contained and adaptable.

Feel free to ask if you need any further assistance or have any questions!




------------------------------

import RuleEngineWrapper from './ruleEngineWrapper';
import { DynamicFact } from './types';
import dotenv from 'dotenv';
import debug from 'debug';

dotenv.config();

const log = debug('rule-engine');

// Enable debugging if DEBUG environment variable is set
if (process.env.DEBUG) {
  log.enabled = true;
}

// Initialize Rule Engine Wrapper
const ruleEngineWrapper = new RuleEngineWrapper();

// Define the hard-coded values
const bucket = process.env.S3_BUCKET!;
const key = process.env.S3_KEY!;

// Define Dynamic Facts
const getNextTradingDate: DynamicFact = async (params, almanac) => {
  const query = "SELECT trade_date FROM calendar WHERE trade_date_indicator = 'Y' AND trade_date > NOW() ORDER BY trade_date LIMIT 1";
  const data = await ruleEngineWrapper.fetchFactData(query);
  log('Next Trading Date:', data[0].trade_date);
  data[0].trade_date = new Date(data[0].trade_date); // Parse the date
  return data[0];
};

const getEligibleInstruments: DynamicFact = async (params, almanac) => {
  const query = `
    SELECT 
      i.instrument_code AS instrument_id,
      i.commodity_code,
      i.instrument_type,
      i.first_trading_date,
      i.last_trading_date,
      ac.commodity_code as applicable_commodity_code,
      ac.instrument_type as applicable_instrument_type,
      ttm.trade_type,
      ttm.commodity_code as trade_commodity_code,
      ttm.instrument_type as trade_instrument_type
    FROM instruments i
    JOIN applicable_contracts ac ON i.commodity_code = ac.commodity_code AND i.instrument_type = ac.instrument_type
    JOIN trade_type_mapping ttm ON i.commodity_code = ttm.commodity_code AND i.instrument_type = ttm.instrument_type
  `;
  const data = await ruleEngineWrapper.fetchFactData(query);
  log('Fetched Instruments:', data);

  // Parse the dates
  data.forEach(instrument => {
    instrument.first_trading_date = new Date(instrument.first_trading_date);
    instrument.last_trading_date = new Date(instrument.last_trading_date);
  });

  log('Parsed Instruments:', data);
  return data;
};

const dynamicFacts: { [key: string]: DynamicFact } = {
  "nextTradingDate": getNextTradingDate,
  "eligibleInstruments": getEligibleInstruments,
  "instrument": (params, almanac) => almanac.factValue("eligibleInstruments")
};

export const handler = async (): Promise<any> => {
  try {
    // Fetch rules from S3 using hard-coded bucket and key
    log('Fetching rules from S3...');
    const rules = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);
    log('Fetched Rules:', rules);

    // Fetch dynamic facts
    const nextTradingDate = await dynamicFacts["nextTradingDate"](null, null);
    const instruments = await dynamicFacts["eligibleInstruments"](null, null);

    log('Next Trading Date:', nextTradingDate);
    log('Instruments:', instruments);

    let eligibleInstruments = [];

    // Evaluate rules for each instrument
    for (let instrument of instruments) {
      const results = await ruleEngineWrapper.evaluateRules(rules, {
        "nextTradingDate": async () => nextTradingDate,
        "instrument": async () => instrument
      });

      // Check if instrument is eligible
      if (results.find(result => result.type === "eligibleInstrument")) {
        eligibleInstruments.push(instrument.instrument_id);
      }
    }

    log('Eligible Instruments:', eligibleInstruments);

    // Insert eligible instruments into the table
    for (const instrument of eligibleInstruments) {
      const insertQuery = 'INSERT INTO eligible_instruments (instrument_id) VALUES ($1)';
      await ruleEngineWrapper.insertProcessedData(insertQuery, [instrument]);
    }

    log('Eligible Instruments inserted into the table successfully.');

    return {
      statusCode: 200,
      body: JSON.stringify({ eligibleInstruments })
    };
  } catch (error) {
    log('Error processing rules:', error);
    return {
      statusCode: 500,
      body: JSON.stringify({ error: 'Internal Server Error' })
    };
  }
};

