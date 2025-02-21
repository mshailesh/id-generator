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




----
Thanks for the clarification. Let's simplify the approach to avoid defining an extra fact for `eligibleInstruments`. We will focus on defining rules that can be applied individually to each instrument and then filtering the instruments based on the evaluation results.

### Simplified Approach:
1. Define rules to check if an instrument's trading dates are within the next trading date.
2. Use the rules engine to evaluate each instrument.
3. Filter the eligible instruments based on the rule evaluation.

### Updated `rules.json`

**`rules.json`**
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
        "message": "Next trading date found",
        "nextTradingDate": "$.trade_date"
      }
    },
    "priority": 10
  },
  {
    "conditions": {
      "all": [
        {
          "fact": "firstTradingDate",
          "operator": "lessThanInclusive",
          "value": {
            "fact": "nextTradingDate",
            "path": "$.trade_date"
          }
        },
        {
          "fact": "lastTradingDate",
          "operator": "greaterThanInclusive",
          "value": {
            "fact": "nextTradingDate",
            "path": "$.trade_date"
          }
        }
      ]
    },
    "event": {
      "type": "eligibleInstrument",
      "params": {
        "message": "Instrument is eligible",
        "instrumentId": "$instrument.instrument_id",
        "nextTradingDate": "$nextTradingDate.trade_date"
      }
    },
    "priority": 5
  }
]
```

### Updated Lambda Function

**`src/index.ts`**
```typescript
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

export const handler = async (): Promise<any> => {
  try {
    // Fetch rules from S3 using hard-coded bucket and key
    log('Fetching rules from S3...');
    const rules = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);
    log('Fetched Rules:', rules);

    // Fetch dynamic facts
    const nextTradingDate = await getNextTradingDate(null, null);
    const instruments = await getEligibleInstruments(null, null);

    log('Next Trading Date:', nextTradingDate);
    log('Instruments:', instruments);

    let eligibleInstruments = [];

    // Evaluate rules for each instrument
    for (let instrument of instruments) {
      const results = await ruleEngineWrapper.evaluateRules(rules, {
        "nextTradingDate": async () => nextTradingDate,
        "firstTradingDate": async () => ({ first_trading_date: instrument.first_trading_date }),
        "lastTradingDate": async () => ({ last_trading_date: instrument.last_trading_date }),
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
```

### Summary

This updated implementation ensures each instrument is evaluated individually based on its trading dates compared to the next trading date. By simplifying the dynamic facts and using the rules engine appropriately, we avoid the issue of undefined facts and ensure proper filtering of eligible instruments.

Feel free to ask if you need any further assistance or have any questions!



// Function to fetch business days after a given date
const fetchBusinessDays = async (startDate: number, days: number): Promise<number> => {
  const query = `
    SELECT trade_date 
    FROM calendar 
    WHERE trade_date_indicator = 'Y' 
    AND EXTRACT(EPOCH FROM trade_date) > ${startDate}
    ORDER BY trade_date 
    LIMIT ${days}`;
  const data = await fetchFactData(query);
  const businessDate = new Date(data[data.length - 1].trade_date).getTime() / 1000; // Return as Unix timestamp of the last date
  return businessDate;

  --------------------------------------------

  // __tests__/db.test.ts
import db from '../services/db';

describe('Database', () => {
  it('should initialize the database connection', () => {
    expect(db).toBeDefined();
    expect(db.connect).toBeDefined();
  });
});

// __tests__/FactService.test.ts
// tests/__tests__/FactService.test.ts
import FactService from '../../src/services/FactService';
import Database from '../../src/utils/Database';
import { IDatabase } from 'pg-promise';

// Mock Database.getInstance() to return a mock database instance
jest.mock('../../src/utils/Database');

const mockDbInstance = {
  any: jest.fn(),
  none: jest.fn(),
} as unknown as IDatabase<any>;

(Database.getInstance as jest.Mock).mockReturnValue(mockDbInstance);

describe('FactService', () => {
  const factService = new FactService();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch fact data', async () => {
    const mockData = [{ id: 1, name: 'Test' }];
    mockDbInstance.any.mockResolvedValue(mockData);

    const data = await factService.fetchFactData('SELECT * FROM test');
    expect(data).toEqual(mockData);
    expect(mockDbInstance.any).toHaveBeenCalledWith('SELECT * FROM test');
  });

  it('should insert processed data', async () => {
    mockDbInstance.none.mockResolvedValue(undefined);

    await factService.insertProcessedData('INSERT INTO test VALUES ($1, $2)', ['value1', 'value2']);
    expect(mockDbInstance.none).toHaveBeenCalledWith('INSERT INTO test VALUES ($1, $2)', ['value1', 'value2']);
  });
});

// __tests__/RuleService.test.ts
// tests/__tests__/RuleService.test.ts
import { Engine, Rule, Almanac } from 'json-rules-engine';
import RuleService from '../../src/services/RuleService';

// Mock json-rules-engine
jest.mock('json-rules-engine');

const mockEngine = Engine as jest.MockedClass<typeof Engine>;
const mockRule = Rule as jest.MockedClass<typeof Rule>;

describe('RuleService', () => {
  let ruleService: RuleService;
  let mockEngineInstance: jest.Mocked<Engine>;

  beforeEach(() => {
    mockEngineInstance = new mockEngine();
    (mockEngine as jest.Mock).mockReturnValue(mockEngineInstance);
    ruleService = new RuleService();
    jest.clearAllMocks();
  });

  it('should add rules to the engine', () => {
    const rules = [{ conditions: {}, event: {} }];
    ruleService.addRules(rules);
    expect(mockEngineInstance.addRule).toHaveBeenCalledWith(new mockRule(rules[0]));
  });

  it('should evaluate rules with dynamic facts', async () => {
    const dynamicFacts = {
      fact1: jest.fn(async () => ({ value: 'test' })),
    };

    const mockResults = { events: [{ type: 'testEvent', params: {} }] };
    mockEngineInstance.run.mockResolvedValue(mockResults);

    const results = await ruleService.evaluateRules(dynamicFacts);
    expect(results).toEqual(mockResults.events);
    expect(mockEngineInstance.addFact).toHaveBeenCalledWith('fact1', dynamicFacts.fact1);
    expect(mockEngineInstance.run).toHaveBeenCalled();
  });
});


// __tests__/S3Service.test.ts
import { S3 } from 'aws-sdk';
import S3Service from '../services/S3Service';

jest.mock('aws-sdk');

const mockS3 = S3 as jest.MockedClass<typeof S3>;

describe('S3Service', () => {
  let s3Service: S3Service;

  beforeEach(() => {
    s3Service = new S3Service();
    jest.clearAllMocks();
  });

  it('should fetch rules from S3', async () => {
    const bucket = 'test-bucket';
    const key = 'test-key';
    const mockData = { Body: Buffer.from(JSON.stringify([{ conditions: {}, event: {} }])) };
    mockS3.prototype.getObject.mockReturnValue({
      promise: jest.fn().mockResolvedValue(mockData),
    } as any);

    const rules = await s3Service.fetchRulesFromS3(bucket, key);
    expect(rules).toEqual([{ conditions: {}, event: {} }]);
    expect(mockS3.prototype.getObject).toHaveBeenCalledWith({ Bucket: bucket, Key: key });
  });
});





// tests/__tests__/Database.test.ts
import pgPromise from 'pg-promise';
import Database from '../../src/utils/Database';

jest.mock('pg-promise');

const mockPgPromise = pgPromise as jest.MockedFunction<typeof pgPromise>;

describe('Database', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should initialize the database connection', () => {
    const mockInstance = {};
    mockPgPromise.mockReturnValueOnce(() => mockInstance as any);

    const dbInstance = Database.getInstance();
    expect(dbInstance).toBe(mockInstance);
    expect(mockPgPromise).toHaveBeenCalledTimes(1);
  });

  it('should return the same instance for subsequent calls', () => {
    const mockInstance = {};
    mockPgPromise.mockReturnValueOnce(() => mockInstance as any);

    const dbInstance1 = Database.getInstance();
    const dbInstance2 = Database.getInstance();
    
    expect(dbInstance1).toBe(dbInstance2);
    expect(mockPgPromise).toHaveBeenCalledTimes(1);
  });
});


import RuleEngineWrapper from './RuleEngineWrapper';
import FactService from './services/FactService';
import RuleService from './services/RuleService';
import S3Service from './services/S3Service';

jest.mock('./services/FactService');
jest.mock('./services/RuleService');
jest.mock('./services/S3Service');

describe('RuleEngineWrapper', () => {
  let ruleEngineWrapper: RuleEngineWrapper;
  let factServiceMock: jest.Mocked<FactService>;
  let ruleServiceMock: jest.Mocked<RuleService>;
  let s3ServiceMock: jest.Mocked<S3Service>;

  beforeEach(() => {
    factServiceMock = new FactService() as jest.Mocked<FactService>;
    ruleServiceMock = new RuleService() as jest.Mocked<RuleService>;
    s3ServiceMock = new S3Service() as jest.Mocked<S3Service>;

    ruleEngineWrapper = new RuleEngineWrapper();
    (ruleEngineWrapper as any).factService = factServiceMock;
    (ruleEngineWrapper as any).ruleService = ruleServiceMock;
    (ruleEngineWrapper as any).s3Service = s3ServiceMock;
  });

  it('should fetch fact data', async () => {
    const query = 'testQuery';
    const mockData = { data: 'testData' };
    factServiceMock.fetchFactData.mockResolvedValue(mockData);

    const result = await ruleEngineWrapper.fetchFactData(query);
    expect(result).toEqual(mockData);
    expect(factServiceMock.fetchFactData).toHaveBeenCalledWith(query);
  });

  it('should evaluate rules', async () => {
    const rules = [{ id: 1, condition: 'testCondition' }];
    const dynamicFacts = { key: { value: 'testValue' } };
    const mockResult = { result: 'testResult' };
    ruleServiceMock.evaluateRules.mockResolvedValue(mockResult);

    const result = await ruleEngineWrapper.evaluateRules(rules, dynamicFacts);
    expect(result).toEqual(mockResult);
    expect(ruleServiceMock.addRules).toHaveBeenCalledWith(rules);
    expect(ruleServiceMock.evaluateRules).toHaveBeenCalledWith(dynamicFacts);
  });

  it('should fetch rules from S3', async () => {
    const bucket = 'testBucket';
    const key = 'testKey';
    const mockRules = [{ id: 1, condition: 'testCondition' }];
    s3ServiceMock.fetchRulesFromS3.mockResolvedValue(mockRules);

    const result = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);
    expect(result).toEqual(mockRules);
    expect(s3ServiceMock.fetchRulesFromS3).toHaveBeenCalledWith(bucket, key);
  });
});


