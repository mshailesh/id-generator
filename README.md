Sure! Below is the complete code for `FactService`, `RuleService`, `S3Service`, and `RuleEngineWrapper`, along with their respective test files.

### FactService

#### FactService Code

```typescript
// services/FactService.ts
import pgPromise from 'pg-promise';
import logger from '../logger';

type FactData = Record<string, any>;

class FactService {
  private db: pgPromise.IDatabase<any>;

  constructor() {
    const pgp = pgPromise();
    this.db = pgp({
      host: process.env.RDS_HOST,
      port: parseInt(process.env.RDS_PORT, 10),
      user: process.env.RDS_USER,
      password: process.env.RDS_PASSWORD,
      database: process.env.RDS_DATABASE,
    });
  }

  async fetchFactData<T>(query: string): Promise<T[]> {
    logger.debug('Fetching fact data from RDS', { query });
    const data = await this.db.any<T>(query);
    logger.debug('Fact data fetched from RDS', { data });
    return data;
  }
}

export default FactService;
```

#### FactService Test

```typescript
// tests/FactService.test.ts
import FactService from '../services/FactService';
import pgPromise from 'pg-promise';

jest.mock('pg-promise');

describe('FactService', () => {
  let factService: FactService;
  let db: any;

  beforeAll(() => {
    db = {
      any: jest.fn(),
    };
    (pgPromise as jest.Mock).mockReturnValue(() => db);
    factService = new FactService();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch fact data from RDS', async () => {
    const query = 'SELECT * FROM facts';
    const expectedResult = [{ id: 1, name: 'fact1' }];
    db.any.mockResolvedValue(expectedResult);

    const result = await factService.fetchFactData(query);

    expect(db.any).toHaveBeenCalledWith(query);
    expect(result).toEqual(expectedResult);
  });
});
```

### RuleService

#### RuleService Code

```typescript
// services/RuleService.ts
import { Engine, Rule, Fact, Almanac, Event } from 'json-rules-engine';
import logger from '../logger';

type DynamicFact = (params: any, almanac: Almanac) => Promise<any>;

class RuleService {
  private engine: Engine;

  constructor() {
    this.engine = new Engine();
    logger.info('Rule engine initialized');
  }

  addRules(rules: Rule[]): void {
    for (const rule of rules) {
      this.engine.addRule(rule);
    }
    logger.info('Rules added to the engine', { rules });
  }

  async evaluateRules(dynamicFacts: Record<string, DynamicFact>): Promise<Event[]> {
    for (const [factId, factFn] of Object.entries(dynamicFacts)) {
      this.engine.addFact(factId, factFn);
    }
    logger.debug('Dynamic facts added to the engine', { dynamicFacts });

    const { events } = await this.engine.run({});
    logger.info('Rules evaluated', { events });
    return events as Event[];
  }
}

export default RuleService;
```

#### RuleService Test

```typescript
// tests/RuleService.test.ts
import { Engine, Rule, Fact, Almanac, Event } from 'json-rules-engine';
import RuleService from '../services/RuleService';

jest.mock('json-rules-engine');

describe('RuleService', () => {
  let ruleService: RuleService;
  let engine: any;

  beforeAll(() => {
    engine = {
      addRule: jest.fn(),
      addFact: jest.fn(),
      run: jest.fn(),
    };
    (Engine as jest.Mock).mockReturnValue(engine);
    ruleService = new RuleService();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should add rules to the engine', () => {
    const rules: Rule[] = [
      new Rule({
        conditions: {
          all: [{ fact: 'fact1', operator: 'equal', value: true }],
        },
        event: {
          type: 'event1',
          params: { message: 'Event triggered' },
        },
      }),
    ];

    ruleService.addRules(rules);

    expect(engine.addRule).toHaveBeenCalledTimes(rules.length);
    rules.forEach((rule) => expect(engine.addRule).toHaveBeenCalledWith(rule));
  });

  it('should evaluate rules with dynamic facts', async () => {
    const dynamicFacts: Record<string, Fact> = {
      fact1: async (params: any, almanac: Almanac) => true,
    };
    const expectedEvents: Event[] = [{ type: 'event1', params: { message: 'Event triggered' } }];
    engine.run.mockResolvedValue({ events: expectedEvents });

    const result = await ruleService.evaluateRules(dynamicFacts);

    expect(engine.addFact).toHaveBeenCalledTimes(Object.keys(dynamicFacts).length);
    Object.entries(dynamicFacts).forEach(([factId, factFn]) =>
      expect(engine.addFact).toHaveBeenCalledWith(factId, factFn)
    );
    expect(engine.run).toHaveBeenCalled();
    expect(result).toEqual(expectedEvents);
  });
});
```

### S3Service

#### S3Service Code

```typescript
// services/S3Service.ts
import { S3 } from 'aws-sdk';
import * as dotenv from 'dotenv';
import logger from '../logger';

// Load environment variables from .env file
dotenv.config();

class S3Service {
  private s3: S3;

  constructor() {
    const forcePathStyle = process.env.FORCE_PATH_STYLE === 'true';
    const endpoint = process.env.S3_ENDPOINT;

    this.s3 = new S3({
      credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
      },
      region: process.env.AWS_REGION,
      s3ForcePathStyle: forcePathStyle,
      endpoint: endpoint
    });
    logger.info('S3 service initialized', { forcePathStyle, endpoint });
  }

  async fetchRulesFromS3<T>(bucket: string, key: string): Promise<T> {
    const params = { Bucket: bucket, Key: key };
    logger.debug('Fetching rules from S3', { bucket, key });
    const data = await this.s3.getObject(params).promise();
    const rules = JSON.parse(data.Body.toString('utf-8')) as T;
    logger.debug('Rules fetched from S3', { rules });
    return rules;
  }
}

export default S3Service;
```

#### S3Service Test

```typescript
// tests/S3Service.test.ts
import S3Service from '../services/S3Service';
import { S3 } from 'aws-sdk';
import * as dotenv from 'dotenv';

dotenv.config();

jest.mock('aws-sdk');

describe('S3Service', () => {
  let s3Service: S3Service;
  let s3: any;

  beforeAll(() => {
    s3 = {
      getObject: jest.fn().mockReturnThis(),
      promise: jest.fn(),
    };
    (S3 as jest.Mock).mockReturnValue(s3);
    s3Service = new S3Service();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch rules from S3', async () => {
    const bucket = 'test-bucket';
    const key = 'test-key';
    const expectedRules = [{ conditions: { all: [] }, event: { type: 'test' } }];
    const s3Response = { Body: Buffer.from(JSON.stringify(expectedRules)) };
    s3.promise.mockResolvedValue(s3Response);

    const result = await s3Service.fetchRulesFromS3(bucket, key);

    expect(s3.getObject).toHaveBeenCalledWith({ Bucket: bucket, Key: key });
    expect(result).toEqual(expectedRules);
  });
});
```

### RuleEngineWrapper

#### RuleEngineWrapper Code

```typescript
// ruleEngineWrapper.ts
import FactService from './services/FactService';
import RuleService from './services/RuleService';
import S3Service from './services/S3Service';
import { Rule, Fact, Event } from 'json-rules-engine';

type DynamicFact = (params: any, almanac: any) => Promise<any>;

class RuleEngineWrapper {
  private factService: FactService;
  private ruleService: RuleService;
  private s3Service: S3Service;

  constructor() {
    this.factService = new FactService();
    this.ruleService = new RuleService();
    this.s3Service = new S3Service();
  }

  async fetchFactData<T>(query: string): Promise<T[]> {
    return this.factService.fetchFactData(query);
  }

  async evaluateRules(rules: Rule[], dynamicFacts: Record<string, DynamicFact>): Promise<Event[]> {
    this.ruleService.addRules(rules);
    return this.ruleService.evaluateRules(dynamicFacts);
  }

  async fetchRulesFromS3<T>(bucket: string, key: string): Promise<T> {
    return this.s3Service.fetchRulesFromS3(bucket, key);
  }
}

export default RuleEngineWrapper;
```

#### RuleEngineWrapper Test

```typescript
// tests/RuleEngineWrapper.test.ts
// tests/RuleEngineWrapper.test.ts
import RuleEngineWrapper from '../ruleEngineWrapper';
import FactService from '../services/FactService';
import RuleService from '../services/RuleService';
import S3Service from '../services/S3Service';
import { Rule, Event, Fact } from 'json-rules-engine';

jest.mock('../services/FactService');
jest.mock('../services/RuleService');
jest.mock('../services/S3Service');

describe('RuleEngineWrapper', () => {
  let ruleEngineWrapper: RuleEngineWrapper;
  let factService: any;
  let ruleService: any;
  let s3Service: any;

  beforeAll(() => {
    factService = new FactService();
    ruleService = new RuleService();
    s3Service = new S3Service();
    ruleEngineWrapper = new RuleEngineWrapper();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch fact data', async () => {
    const query = 'SELECT * FROM facts';
    const expectedResult = [{ id: 1, name: 'fact1' }];
    factService.fetchFactData.mockResolvedValue(expectedResult);

    const result = await ruleEngineWrapper.fetchFactData(query);

    expect(factService.fetchFactData).toHaveBeenCalledWith(query);
    expect(result).toEqual(expectedResult);
  });

  it('should evaluate rules with dynamic facts', async () => {
    const rules: Rule[] = [
      new Rule({
        conditions: { all: [{ fact: 'fact1', operator: 'equal', value: true }] },
        event: { type: 'event1', params: { message: 'Event triggered' } },
      }),
    ];
    const dynamicFacts: Record<string, Fact> = {
      fact1: async (params: any, almanac: any) => true,
    };
    const expectedEvents: Event[] = [{ type: 'event1', params: { message: 'Event triggered' } }];
    ruleService.evaluateRules.mockResolvedValue(expectedEvents);

    const result = await ruleEngineWrapper.evaluateRules(rules, dynamicFacts);

    expect(ruleService.addRules).toHaveBeenCalledWith(rules);
    expect(ruleService.evaluateRules).toHaveBeenCalledWith(dynamicFacts);
    expect(result).toEqual(expectedEvents);
  });

  it('should fetch rules from S3', async () => {
    const bucket = 'test-bucket';
    const key = 'test-key';
    const expectedRules = [{ conditions: { all: [] }, event: { type: 'test' } }];
    s3Service.fetchRulesFromS3.mockResolvedValue(expectedRules);

    const result = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);

    expect(s3Service.fetchRulesFromS3).toHaveBeenCalledWith(bucket, key);
    expect(result).toEqual(expectedRules);
  });
});





--------------------------------------------------***------------------------

Sure! Let's update the implementation to use AWS Lambda Powertools for logging and include logging statements.

### Directory Structure
```
project-root/
│
├── queries/
│   ├── index.ts
│
├── models/
│   ├── FactData.ts
│   ├── RuleConfig.ts
│
├── utils/
│   ├── logger.ts
│
├── dynamicFacts.ts
├── types.ts
├── handler.ts
├── rules.json
├── tests/
│   ├── handler.test.ts
│   ├── dynamicFacts.test.ts
│
├── .env
└── package.json
```

### Queries

#### index.ts
```typescript
export const getMarketSegmentsQuery = `
  SELECT market_segment_code, calendar_id
  FROM market_segment
  WHERE start_date <= NOW() AND end_date >= NOW()
`;

export const getNextTradingDateQuery = (calendarId: string) => `
  SELECT MIN(c.trade_date) AS next_trade_date
  FROM calendar c
  WHERE c.calendar_id = '${calendarId}'
    AND c.trade_date > NOW()
    AND c.trade_date_indicator = 'Y'
`;

export const getEligibleInstrumentsQuery = (marketSegmentCode: string, nextTradingDate: string) => `
  SELECT 
    i.instrument_code,
    i.commodity_code,
    i.instrument_type,
    i.first_trading_date,
    i.last_trading_date,
    i.expiry_date,
    ac.commodity_code as applicable_commodity_code,
    ac.instrument_type as applicable_instrument_type,
    ttm.trade_type,
    ttm.commodity_code as trade_commodity_code,
    ttm.instrument_type as trade_instrument_type,
    ms.market_segment_code,
    c.trade_date
  FROM instruments i
  JOIN applicable_contracts ac ON i.commodity_code = ac.commodity_code AND i.instrument_type = ac.instrument_type
  JOIN trade_type_mapping ttm ON i.commodity_code = ttm.commodity_code AND i.instrument_type = ttm.instrument_type
  JOIN market_segment ms ON i.market_segment_code = ms.market_segment_code
  JOIN calendar c ON ms.calendar_id = c.calendar_id
  WHERE ms.market_segment_code = '${marketSegmentCode}'
    AND c.trade_date = '${nextTradingDate}'
    AND ttm.trade_type IN ('block', 'efp', 'delivery efp')
    AND i.commodity_code IN (SELECT commodity_code FROM applicable_contracts)
    AND i.market_segment_code = '${marketSegmentCode}'
    AND (
      (ttm.trade_type = 'delivery efp' AND c.trade_date BETWEEN i.last_trading_date AND i.expiry_date)
      OR (ttm.trade_type IN ('block', 'efp') AND c.trade_date BETWEEN i.first_trading_date AND i.last_trading_date)
    )
`;
```

### Models

#### FactData.ts
```typescript
export interface FactData {
  instrument_code: string;
  commodity_code: string;
  instrument_type: string;
  first_trading_date: string;
  last_trading_date: string;
  expiry_date: string;
  trade_type: string;
  trade_subtype: string;
  market_segment_code: string;
  // Add other fields as needed
}
```

#### RuleConfig.ts
```typescript
export interface RuleConfig {
  bucket: string;
  key: string;
  dynamicFacts: Record<string, any>;
}
```

### Utilities

#### logger.ts
```typescript
import { Logger } from '@aws-lambda-powertools/logger';

const logger = new Logger({ serviceName: 'YourServiceName' });

export default logger;
```

### Dynamic Facts

#### dynamicFacts.ts
```typescript
import { getMarketSegmentsQuery, getNextTradingDateQuery, getEligibleInstrumentsQuery } from './queries/index';
import { FactService, RuleService, S3Service } from 'your-library';
import logger from './utils/logger';

const ruleEngineWrapper = new RuleEngineWrapper(new FactService(process.env), new RuleService(), new S3Service());

const getMarketSegments = async () => {
  logger.info('Fetching market segments');
  const data = await ruleEngineWrapper.fetchFactData(getMarketSegmentsQuery);
  logger.info('Market segments fetched', { data });
  return data;
};

const getNextTradingDate = async (params, almanac) => {
  const marketSegments = await almanac.factValue("marketSegments");

  const queries = marketSegments.map(segment => getNextTradingDateQuery(segment.calendar_id));
  const promises = queries.map(query => ruleEngineWrapper.fetchFactData(query));
  const results = await Promise.all(promises);
  logger.info('Next trading dates fetched', { results });
  return results.map(res => res[0]);
};

const getEligibleInstruments = async (params, almanac) => {
  const marketSegments = await almanac.factValue("marketSegments");
  const nextTradingDates = await almanac.factValue("nextTradingDate");

  const queries = marketSegments.map((segment, index) => 
    getEligibleInstrumentsQuery(segment.market_segment_code, nextTradingDates[index].next_trade_date)
  );
  
  const promises = queries.map(query => ruleEngineWrapper.fetchFactData(query));
  const results = await Promise.all(promises);
  logger.info('Eligible instruments fetched', { results });
  return results.flat();
};

export const dynamicFacts: { [key: string]: any } = {
  marketSegments: getMarketSegments,
  nextTradingDate: getNextTradingDate,
  eligibleInstruments: getEligibleInstruments
};
```

### Lambda Handler

#### handler.ts
```typescript
import { APIGatewayProxyHandler } from 'aws-lambda';
import { dynamicFacts } from './dynamicFacts';
import { RuleConfig } from './models/RuleConfig';
import logger from './utils/logger';
import { RuleEngineWrapper, FactService, RuleService, S3Service } from 'your-library';

const ruleEngineWrapper = new RuleEngineWrapper(new FactService(process.env), new RuleService(), new S3Service());

export const handler: APIGatewayProxyHandler = async (event) => {
  try {
    logger.info('Handler invoked', { event });
    const { bucket, key } = JSON.parse(event.body!) as RuleConfig;

    // Fetch rules from S3
    const rules = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);
    logger.info('Rules fetched from S3', { rules });

    // Evaluate rules with dynamic facts
    const results = await ruleEngineWrapper.evaluateRules(rules, dynamicFacts);
    logger.info('Rules evaluated', { results });

    return {
      statusCode: 200,
      body: JSON.stringify(results),
    };
  } catch (error) {
    logger.error('Error in handler', { error });
    return {
      statusCode: 500,
      body: JSON.stringify({ error: 'Internal Server Error' }),
    };
  }
};
```

### Rules Definition

#### rules.json
```json
[
  {
    "conditions": {
      "all": [
        {
          "fact": "marketSegments",
          "operator": "greaterThanInclusive",
          "value": "$today",
          "path": "$.market_segment_code"
        },
        {
          "fact": "nextTradingDate",
          "operator": "greaterThanInclusive",
          "value": "$today",
          "path": "$.next_trade_date"
        }
      ]
    },
    "event": {
      "type": "nextTradingDate",
      "params": {
        "message": "Next trading date found"
      }
    },
    "priority": 10
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
          "fact": "eligibleInstruments",
          "operator": "in",
          "value": ["block", "efp"],
          "path": "$.trade_subtype"
        },
        {
          "fact": "eligibleInstruments",
          "operator": "equal",
          "value": "$marketSegmentCode",
          "path": "$.market_segment_code"
        }
      ]
    },
    "event": {
      "type": "blockAndEfpInstrument",
      "params": {
        "message": "Block and EFP instrument is eligible"
      }
    },
    "priority": 5
  },
  {
    "conditions": {
      "all": [
        {
          "fact": "eligibleInstruments",
          "operator": "greaterThanInclusive",
          "value": "$nextTradingDate",
          "path": "$.last_trading_date"
        },
        {
          "fact": "eligibleInstruments",
          "operator": "lessThanInclusive",
          "value": "$nextTradingDate",
          "path": "$.expiry_date"
        },
        {
          "fact": "eligibleInstruments",
          "operator": "equal",
          "value": "delivery efp",
          "path": "$.trade_type"
        },
        {
          "fact": "eligibleInstruments",
          "operator": "equal",
          "value": "$marketSegmentCode",
          "path": "$.market_segment_code"
        }
      ]
    },
    "event": {
      "type": "deliveryEfpInstrument",
      "params": {
        "message": "Delivery EFP instrument is eligible"
      }
    },
    "priority": 5
  }



 async evaluateRulesWithParamsInjection(
    dynamicFacts: Record<string, DynamicFact>,
    paramsInjector: (event: Event, almanac: Almanac) => Promise<Record<string, any>>
  ): Promise<Event[]> {
    for (const [factId, factFn] of Object.entries(dynamicFacts)) {
      this.engine.addFact(factId, factFn);
    }
    logger.debug('Dynamic facts added to the engine', { dynamicFacts });

    const { events } = await this.engine.run({});

    // Inject additional parameters into event params
    for (const event of events) {
      const additionalParams = await paramsInjector(event, this.engine.almanac);
      Object.assign(event.params, additionalParams); // Merge additional params into event.params
    }

    logger.info('Rules evaluated with injected params', { events });
    return events as Event[];
  }



// Define a params injector function
const paramsInjector = async (event: Event, almanac: Almanac) => {
  const instrumentDetails = await almanac.factValue('instrumentDetails'); // Retrieve instrument details dynamically
  return { instrument_code: instrumentDetails.instrument_code }; // Inject instrument_code into event params
};


interface Instrument {
  trade_sub_type: string;
  commodity_code: string;
  instrument_type: string;
  instrument_code: string;
  expiry_date: string;
}

interface SequencedInstrument extends Instrument {
  sequence_number: number;
}

function assignSequenceNumbersByGroup(instruments: Instrument[]): SequencedInstrument[] {
  // Sort instruments by the group key and expiry_date to maintain order
  instruments.sort((a, b) => {
    return `${a.trade_sub_type}-${a.commodity_code}-${a.instrument_type}-${a.expiry_date}`.localeCompare(
      `${b.trade_sub_type}-${b.commodity_code}-${b.instrument_type}-${b.expiry_date}`
    );
  });

  const result: SequencedInstrument[] = [];
  let sequenceNumber = 0;
  let previousGroupKey = '';

  for (const instrument of instruments) {
    // Generate the group key based on trade_sub_type, commodity_code, instrument_type, and expiry_date
    const currentGroupKey = `${instrument.trade_sub_type}-${instrument.commodity_code}-${instrument.instrument_type}-${instrument.expiry_date}`;

    // Increment sequence number only if the group key changes
    if (currentGroupKey !== previousGroupKey) {
      sequenceNumber++; // Increment sequence number for the new group
      previousGroupKey = currentGroupKey; // Update the previous group key
    }

    result.push({
      ...instrument,
      sequence_number: sequenceNumber,
    });
  }





To handle **multiple rules** where each rule is applied to an instrument sequentially, and if any rule fails for an instrument, that instrument is skipped, we can refactor the code to process each instrument individually. This ensures that if an instrument fails a rule, it is discarded immediately, and the next instrument is processed.

Here’s how we can achieve this:

---

### **1. Key Requirements**
1. **Load All Instruments at Once:** Fetch all instruments from the database in a single query.
2. **Process Each Instrument Individually:** Apply rules sequentially to each instrument.
3. **Skip Instrument if Any Rule Fails:** If an instrument fails any rule, discard it and move to the next instrument.
4. **Store Valid Instruments:** Only instruments that pass all rules are stored in the database.

---

### **2. Refactored Code Structure**
We will refactor the code into the following components:
1. **Instrument Loader:** Fetches all instruments from the database.
2. **Rule Handlers:** Apply rules sequentially to each instrument.
3. **Instrument Processor:** Loops through each instrument and applies the rule chain.
4. **Database Storage Handler:** Stores valid instruments in the database.

---

### **3. Refactored Implementation**

#### **3.1. Instrument Loader**
This component is responsible for loading all instruments from the database at once.

```typescript
class InstrumentLoader {
  async loadInstruments(): Promise<any[]> {
    console.log("Loading all instruments from the database...");
    const instruments = await loadAllInstrumentsFromDB(); // Fetch all instruments
    return instruments;
  }
}

// Helper function to simulate database query
async function loadAllInstrumentsFromDB(): Promise<any[]> {
  return [
    { id: "1", rule1Condition: true, rule2Condition: true },
    { id: "2", rule1Condition: false, rule2Condition: true }, // Fails Rule 1
    { id: "3", rule1Condition: true, rule2Condition: false }, // Fails Rule 2
    { id: "4", rule1Condition: true, rule2Condition: true },
  ];
}
```

---

#### **3.2. Rule Handlers**
Each rule handler applies a specific rule to the instrument. If the rule fails, it throws an error.

```typescript
abstract class RuleHandler {
  private nextHandler: RuleHandler | null = null;

  setNext(handler: RuleHandler): RuleHandler {
    this.nextHandler = handler;
    return handler;
  }

  async handle(instrument: any): Promise<any> {
    try {
      // Apply the rule
      const processedInstrument = await this.applyRule(instrument);

      // Pass to the next rule handler
      if (this.nextHandler) {
        return this.nextHandler.handle(processedInstrument);
      }

      // Return the instrument if no more handlers
      return processedInstrument;
    } catch (error) {
      // Stop processing for this instrument if the rule fails
      throw error;
    }
  }

  // Abstract method to be implemented by concrete handlers
  abstract applyRule(instrument: any): Promise<any>;
}

class Rule1Handler extends RuleHandler {
  async applyRule(instrument: any): Promise<any> {
    console.log(`Applying Rule 1 to instrument ${instrument.id}`);
    if (!instrument.rule1Condition) {
      throw new Error(`Instrument ${instrument.id} failed Rule 1`);
    }
    return instrument;
  }
}

class Rule2Handler extends RuleHandler {
  async applyRule(instrument: any): Promise<any> {
    console.log(`Applying Rule 2 to instrument ${instrument.id}`);
    if (!instrument.rule2Condition) {
      throw new Error(`Instrument ${instrument.id} failed Rule 2`);
    }
    return instrument;
  }
}
```

---

#### **3.3. Instrument Processor**
This component loops through each instrument and applies the rule chain. If an instrument passes all rules, it is added to the list of valid instruments.

```typescript
class InstrumentProcessor {
  private ruleHandlerChain: RuleHandler;

  constructor(ruleHandlerChain: RuleHandler) {
    this.ruleHandlerChain = ruleHandlerChain;
  }

  async processInstruments(instruments: any[]): Promise<any[]> {
    const validInstruments: any[] = [];

    for (const instrument of instruments) {
      try {
        // Apply the rule chain to the instrument
        const processedInstrument = await this.ruleHandlerChain.handle(instrument);
        validInstruments.push(processedInstrument);
      } catch (error) {
        console.error(`Instrument ${instrument.id} failed:`, error.message);
      }
    }

    return validInstruments;
  }
}
```

---

#### **3.4. Database Storage Handler**
This component stores valid instruments in the database.

```typescript
class DatabaseStorageHandler {
  async storeInstruments(instruments: any[]): Promise<void> {
    console.log("Storing valid instruments in the database...");
    for (const instrument of instruments) {
      await storeInstrumentInDB(instrument);
    }
  }
}

// Helper function to simulate database storage
async function storeInstrumentInDB(instrument: any): Promise<void> {
  console.log(`Storing instrument ${instrument.id}`);
}
```

---

#### **3.5. Lambda Handler**
The Lambda handler sets up the chain of responsibility, loads all instruments, processes them, and stores the valid ones.

```typescript
const handler = async (event: any) => {
  const instrumentLoader = new InstrumentLoader();
  const instruments = await instrumentLoader.loadInstruments();

  const rule1Handler = new Rule1Handler();
  const rule2Handler = new Rule2Handler();
  rule1Handler.setNext(rule2Handler);

  const instrumentProcessor = new InstrumentProcessor(rule1Handler);
  const validInstruments = await instrumentProcessor.processInstruments(instruments);

  const dbHandler = new DatabaseStorageHandler();
  await dbHandler.storeInstruments(validInstruments);

  return {
    statusCode: 200,
    body: JSON.stringify({ message: "Processing complete", validInstruments }),
  };
};

export { handler };
```

---

### **4. Test Cases**

We will use a testing framework like **Jest** to write test cases.

#### **4.1. Setup Jest**
Install Jest if not already installed:

```bash
npm install --save-dev jest
```

Add a `jest.config.js` file:

```javascript
module.exports = {
  testEnvironment: "node",
};
```

---

#### **4.2. Test Cases**

```typescript
const { handler } = require("./path/to/your/lambda/handler");

describe("Instrument Processing", () => {
  it("should load instruments, apply rules, and store valid instruments", async () => {
    const event = {}; // Mock event
    const result = await handler(event);

    // Check if the response is correct
    expect(result.statusCode).toBe(200);
    expect(result.body).toContain("Processing complete");

    // Check if valid instruments are stored
    const responseBody = JSON.parse(result.body);
    expect(responseBody.validInstruments).toEqual([
      { id: "1", rule1Condition: true, rule2Condition: true },
      { id: "4", rule1Condition: true, rule2Condition: true },
    ]);
  });

  it("should discard instruments that fail Rule 1", async () => {
    const instruments = [
      { id: "1", rule1Condition: true, rule2Condition: true },
      { id: "2", rule1Condition: false, rule2Condition: true }, // Fails Rule 1
    ];

    const rule1Handler = new Rule1Handler();
    const instrumentProcessor = new InstrumentProcessor(rule1Handler);
    const validInstruments = await instrumentProcessor.processInstruments(instruments);

    expect(validInstruments).toEqual([
      { id: "1", rule1Condition: true, rule2Condition: true },
    ]);
  });

  it("should discard instruments that fail Rule 2", async () => {
    const instruments = [
      { id: "1", rule1Condition: true, rule2Condition: true },
      { id: "3", rule1Condition: true, rule2Condition: false }, // Fails Rule 2
    ];

    const rule1Handler = new Rule1Handler();
    const rule2Handler = new Rule2Handler();
    rule1Handler.setNext(rule2Handler);

    const instrumentProcessor = new InstrumentProcessor(rule1Handler);
    const validInstruments = await instrumentProcessor.processInstruments(instruments);

    expect(validInstruments).toEqual([
      { id: "1", rule1Condition: true, rule2Condition: true },
    ]);
  });

  it("should handle empty instrument list", async () => {
    const instruments = [];

    const rule1Handler = new Rule1Handler();
    const instrumentProcessor = new InstrumentProcessor(rule1Handler);
    const validInstruments = await instrumentProcessor.processInstruments(instruments);

    expect(validInstruments).toEqual([]);
  });
});
```

---

### **5. Running Tests**
Run the tests using Jest:

```bash
npx jest
```

---

### **6. Expected Output**
- **Test 1:** Valid instruments (`id: 1` and `id: 4`) are processed and stored.
- **Test 2:** Instruments failing Rule 1 are discarded.
- **Test 3:** Instruments failing Rule 2 are discarded.
- **Test 4:** Empty instrument list is handled gracefully.

---

### **7. Benefits**
- **Efficient Processing:** Instruments are processed one at a time, ensuring rules are applied correctly.
- **Error Handling:** If a rule fails for an instrument, it is discarded immediately.
- **Modularity:** Each rule handler is independent and can be modified or extended easily.
- **Scalability:** New rules can be added to the chain without affecting existing logic.

This approach ensures that the system is robust, maintainable, and adheres to clean code principles while meeting the specific requirements.

  return result;
}


To better structure your code in a maintainable and modular way, here's how you can organize the different components into separate files. This approach adheres to clean code principles and ensures reusability and scalability.

---

### **Proposed Folder and File Structure**
Here's a suggested folder structure for your project:

```
src/
│
├── handlers/
│   ├── RuleHandler.ts
│   ├── Rule1Handler.ts
│   ├── Rule2Handler.ts
│   ├── SequencingHandler.ts
│   ├── Rule3Handler.ts
│
├── processors/
│   ├── InstrumentProcessor.ts
│
├── utils/
│   ├── SequencingUtil.ts
│   ├── DatabaseStorageHandler.ts
│
├── loaders/
│   ├── InstrumentLoader.ts
│
├── types/
│   ├── Instrument.ts
│
├── tests/
│   ├── LambdaHandler.test.ts
│
├── lambda/
│   ├── Handler.ts
│
├── index.ts
```

---

### **Detailed Breakdown**
1. **`handlers/` Folder:**
   - Contains all the rule handlers, each implementing specific logic while inheriting from the abstract `RuleHandler`.

   **Example: RuleHandler.ts**
   ```typescript
   export abstract class RuleHandler {
     private nextHandler: RuleHandler | null = null;

     setNext(handler: RuleHandler): RuleHandler {
       this.nextHandler = handler;
       return handler;
     }

     async handle(instrument: any): Promise<any> {
       if (!(await this.applyRule(instrument))) {
         console.log(`Instrument ${instrument.id} failed ${this.constructor.name}`);
         return null;
       }
       return this.nextHandler ? this.nextHandler.handle(instrument) : instrument;
     }

     abstract applyRule(instrument: any): Promise<boolean>;
   }
   ```

   **Example: Rule1Handler.ts**
   ```typescript
   import { RuleHandler } from "./RuleHandler";

   export class Rule1Handler extends RuleHandler {
     async applyRule(instrument: any): Promise<boolean> {
       return instrument.rule1Condition || false;
     }
   }
   ```

   Repeat for `Rule2Handler`, `SequencingHandler`, and `Rule3Handler`.

2. **`processors/` Folder:**
   - Contains the core `InstrumentProcessor`, which is responsible for processing the instruments.

   **Example: InstrumentProcessor.ts**
   ```typescript
   import { RuleHandler } from "../handlers/RuleHandler";

   export class InstrumentProcessor {
     private ruleHandlerChain: RuleHandler;

     constructor(ruleHandlerChain: RuleHandler) {
       this.ruleHandlerChain = ruleHandlerChain;
     }

     async processInstruments(instruments: any[]): Promise<any[]> {
       const processedInstruments = [];
       for (const instrument of instruments) {
         const result = await this.ruleHandlerChain.handle(instrument);
         if (result) {
           processedInstruments.push(result);
         }
       }
       return processedInstruments;
     }
   }
   ```

3. **`utils/` Folder:**
   - Contains utility functions or classes for generic operations like sequencing and database handling.

   **Example: SequencingUtil.ts**
   ```typescript
   export function assignSequenceNumbersByGroup(instruments: any[]): any[] {
     instruments.sort((a, b) => {
       return `${a.trade_sub_type}-${a.commodity_code}-${a.instrument_type}-${a.expiry_date}`.localeCompare(
         `${b.trade_sub_type}-${b.commodity_code}-${b.instrument_type}-${b.expiry_date}`
       );
     });

     let sequenceNumber = 0;
     let previousGroupKey = "";
     const result = [];

     for (const instrument of instruments) {
       const currentGroupKey = `${instrument.trade_sub_type}-${instrument.commodity_code}-${instrument.instrument_type}-${instrument.expiry_date}`;
       if (currentGroupKey !== previousGroupKey) {
         sequenceNumber++;
         previousGroupKey = currentGroupKey;
       }
       result.push({ ...instrument, sequence_number: sequenceNumber });
     }

     return result;
   }
   ```

   **Example: DatabaseStorageHandler.ts**
   ```typescript
   export class DatabaseStorageHandler {
     async storeInstruments(instruments: any[]): Promise<void> {
       console.log("Storing instruments:", instruments.map((i) => i.id).join(", "));
     }
   }
   ```

4. **`loaders/` Folder:**
   - Contains the `InstrumentLoader`, which fetches instruments from the database.

   **Example: InstrumentLoader.ts**
   ```typescript
   export class InstrumentLoader {
     async loadInstruments(): Promise<any[]> {
       return [
         { id: "1", rule1Condition: true, rule2Condition: true, rule3Condition: true },
         { id: "2", rule1Condition: false, rule2Condition: true, rule3Condition: true },
       ];
     }
   }
   ```

5. **`types/` Folder:**
   - Contains type definitions for clarity and strict type-checking.

   **Example: Instrument.ts**
   ```typescript
   export type Instrument = {
     id: string;
     trade_sub_type: string;
     commodity_code: string;
     instrument_type: string;
     expiry_date: string;
     rule1Condition?: boolean;
     rule2Condition?: boolean;
     rule3Condition?: boolean;
   };
   ```

6. **`tests/` Folder:**
   - Contains test files for unit and integration testing.

   **Example: LambdaHandler.test.ts**
   ```typescript
   import { handler } from "../lambda/Handler";

   describe("Lambda Handler", () => {
     it("should process instruments correctly", async () => {
       const response = await handler({});
       expect(response.statusCode).toBe(200);
       const body = JSON.parse(response.body);
       expect(body.validInstruments.length).toBeGreaterThan(0);
     });
   });
   ```

7. **`lambda/` Folder:**
   - Contains the entry point for your Lambda function.

   **Example: Handler.ts**
   ```typescript
   import { InstrumentLoader } from "../loaders/InstrumentLoader";
   import { Rule1Handler } from "../handlers/Rule1Handler";
   import { Rule2Handler } from "../handlers/Rule2Handler";
   import { SequencingHandler } from "../handlers/SequencingHandler";
   import { Rule3Handler } from "../handlers/Rule3Handler";
   import { InstrumentProcessor } from "../processors/InstrumentProcessor";
   import { DatabaseStorageHandler } from "../utils/DatabaseStorageHandler";
   import { assignSequenceNumbersByGroup } from "../utils/SequencingUtil";

   export const handler = async (event: any) => {
     const loader = new InstrumentLoader();
     const instruments = await loader.loadInstruments();

     const rule1 = new Rule1Handler();
     const rule2 = new Rule2Handler();
     const sequencing = new SequencingHandler();
     const rule3 = new Rule3Handler();

     rule1.setNext(rule2).setNext(sequencing).setNext(rule3);

     const processor = new InstrumentProcessor(rule1);
     const validInstruments = await processor.processInstruments(instruments);

     const sequencedInstruments = assignSequenceNumbersByGroup(validInstruments);

     const dbHandler = new DatabaseStorageHandler();
     await dbHandler.storeInstruments(sequencedInstruments);

     return {
       statusCode: 200,
       body: JSON.stringify({ message: "Processing complete", validInstruments: sequencedInstruments }),
     };
   };
   ```

---

async batchInsertInstruments(instruments: any[]): Promise<void> {
    if (instruments.length === 0) {
      logger.info('No instruments to insert.');
      return;
    }

    // Define the table and columns for the bulk insert
    const table = new pgPromise.helpers.TableName('processed_instruments'); // Replace with your table name
    const columns = ['id', 'trade_date', 'rule1_condition', 'rule2_condition', 'rule3_condition', 'priority', 'sequence_number']; // Replace with your column names

    // Generate the bulk insert query
    const query = pgPromise.helpers.insert(instruments, columns, table);

    try {
      // Execute the bulk insert query
      await this.db.none(query);
      logger.info(`Successfully inserted ${instruments.length} instruments.`);
    } catch (error) {
      logger.error('Failed to insert instruments:', error);
      throw error;
    }
  }

  function mapInstrumentsToColumns(instruments: any[]): any[] {
  return instruments.map((instrument) => ({
    id: instrument.id, // Map instrument ID
    trade_date: instrument.tradeDate, // Map tradeDate to trade_date
    rule1_condition: instrument.rule1Condition, // Map rule1Condition to rule1_condition
    rule2_condition: instrument.rule2Condition, // Map rule2Condition to rule2_condition
    rule3_condition: instrument.rule3Condition, // Map rule3Condition to rule3_condition
    priority: instrument.priority, // Map priority
    sequence_number: instrument.sequenceNumber, // Map sequenceNumber to sequence_number
  }));
}

export default mapInstrumentsToColumns;

Sure! I’ll break the `mergeInstruments` method from your `FactService` class into smaller, reusable functions for clarity and maintainability. Each function will handle a specific part of the logic, resulting in cleaner, more modular code. Here's the refactored version:

---

### Refactored `FactService` with Smaller Functions

```typescript
import pgPromise from 'pg-promise';
import logger from '../logger';

class FactService {
  private db: pgPromise.IDatabase<any>;

  constructor() {
    const pgp = pgPromise();
    this.db = pgp({
      host: process.env.RDS_HOST,
      port: parseInt(process.env.RDS_PORT, 10),
      user: process.env.RDS_USER,
      password: process.env.RDS_PASSWORD,
      database: process.env.RDS_DATABASE,
    });
  }

  // Generic method to fetch data
  async fetchFactData<T>(query: string, params: any[] = []): Promise<T[]> {
    logger.debug('Fetching fact data', { query, params });
    return this.db.any<T>(query, params);
  }

  // Split function to prepare column names for placeholders
  private preparePlaceholders(
    data: Record<string, any>,
    specialFields: { dateFields: string[]; bitFields: string[]; timestampFields: string[] }
  ): string {
    const { dateFields, bitFields, timestampFields } = specialFields;

    return Object.keys(data)
      .map((key, index) => {
        if (dateFields.includes(key)) {
          return `$${index + 1}::DATE`;
        } else if (bitFields.includes(key)) {
          return `$${index + 1}::BIT(1)`;
        } else if (timestampFields.includes(key)) {
          return `$${index + 1}::TIMESTAMP`;
        }
        return `$${index + 1}`;
      })
      .join(', ');
  }

  // Split function to create ON condition dynamically
  private prepareOnCondition(onColumns: string[]): string {
    return onColumns.map((col) => `target.${col} = source.${col}`).join(' AND ');
  }

  // Split function to preprocess data for special fields
  private processData(
    data: Record<string, any>,
    specialFields: { dateFields: string[]; bitFields: string[]; timestampFields: string[] }
  ): any[] {
    const { dateFields, bitFields, timestampFields } = specialFields;

    return Object.keys(data).map((key) => {
      if (dateFields.includes(key)) {
        return data[key] instanceof Date
          ? (data[key] as Date).toISOString().split('T')[0] // Convert Date object to YYYY-MM-DD
          : data[key];
      } else if (bitFields.includes(key)) {
        return data[key] === '0' || data[key] === '1' ? data[key] : '0'; // Validate BIT field
      } else if (timestampFields.includes(key)) {
        return data[key] instanceof Date
          ? (data[key] as Date).toISOString() // Convert Date to full ISO timestamp
          : data[key];
      }
      return data[key];
    });
  }

  // Main mergeInstruments function orchestrating the smaller functions
  async mergeInstruments(
    tableName: string,
    dataList: Record<string, any>[],
    onColumns: string[],
    specialFields: {
      dateFields: string[];
      bitFields: string[];
      timestampFields: string[];
    }
  ): Promise<void> {
    if (!dataList.length) {
      logger.info('No data to merge');
      return;
    }

    // Prepare query components
    const firstEntry = dataList[0];
    const columns = Object.keys(firstEntry).join(', ');
    const placeholders = this.preparePlaceholders(firstEntry, specialFields);
    const onCondition = this.prepareOnCondition(onColumns);

    const query = `
      MERGE INTO ${tableName} AS target
      USING (VALUES (${placeholders})) AS source (${columns})
      ON ${onCondition}
      WHEN MATCHED THEN
        DO NOTHING
      WHEN NOT MATCHED THEN
        INSERT (${columns})
        VALUES (${columns.split(', ').map((col) => `source.${col}`).join(', ')});
    `;

    // Execute the query for each row of data
    for (const data of dataList) {
      const values = this.processData(data, specialFields);

      try {
        logger.debug('Executing MERGE query', { query, values });
        await this.db.none(query, values);
      } catch (error) {
        logger.error('Error executing MERGE query', { error, data });
        throw error;
      }
    }

    logger.info('Merge complete for all instruments');
  }
}

export default FactService;
```

---

### Key Refactor Details

1. **`preparePlaceholders`:**
   - Generates placeholders for all columns dynamically.
   - Handles type-specific placeholders for `dateFields`, `bitFields`, and `timestampFields`.

2. **`prepareOnCondition`:**
   - Dynamically constructs the `ON` condition for the `MERGE` query using `onColumns`.

3. **`processData`:**
   - Handles special field processing for each row in `dataList`.
   - Converts `DATE` and `TIMESTAMP` fields to appropriate formats and validates `BIT` fields.

4. **Main `mergeInstruments`:**
   - Orchestrates the query-building process by calling the smaller, focused functions.
   - Executes the final query for each entry in `dataList`.

---

### Example Handler with Special Fields

```typescript
import FactService from './services/FactService';

const factService = new FactService();

export const handler = async (event: any): Promise<any> => {
  const { instruments } = event;
  const tableName = 'instruments';
  const onColumns = ['instrument_code', 'commodity_code'];

  const specialFields = {
    dateFields: ['trade_date', 'another_date_field'],
    bitFields: ['bit_field', 'another_bit_field'],
    timestampFields: ['timestamp_field', 'another_timestamp_field'],
  };

  try {
    if (!instruments || !instruments.length) {
      return {
        statusCode: 400,
        body: JSON.stringify({ error: 'No data provided' }),
      };
    }

    await factService.mergeInstruments(tableName, instruments, onColumns, specialFields);

    return {
      statusCode: 200,
      body: JSON.stringify({ message: 'Merge operation completed successfully' }),
    };
  } catch (error) {
    console.error('Error in handler:', error);

    return {
      statusCode: 500,
      body: JSON.stringify({
        error: 'Internal Server Error',
        details: error.message,
      }),
    };
  }
};
```

---

### Advantages of the Refactor
1. **Smaller, Modular Functions:**
   - Each function handles a specific part of the logic, making the code more readable and maintainable.
2. **Generic Handling:**
   - Can be easily extended to handle other special field types in the future.
3. **Cleaner Orchestration:**
   - The `mergeInstruments` function is now focused on orchestrating the smaller reusable functions.

This refactor makes it easy to test individual components and apply further improvements without affecting the entire structure. Let me know if you'd like any further customizations!



If we remove the `processData` function, we would lose the centralized logic for validating and formatting specific fields like `DATE`, `TIMESTAMP`, `UUID`, `NUMERIC`, and `BIT`. This means that all type handling and preprocessing would need to happen inline within the loop where we prepare `values` for each row of `dataList`. This can lead to cluttered and less readable code, as well as duplicate logic.

Let me show you how the code would look **without `processData`**, keeping things clean but sacrificing the benefits of centralized data handling.

---

### Refactored Code Without `processData`

```typescript
import pgPromise from 'pg-promise';
import logger from '../logger';

class FactService {
  private db: pgPromise.IDatabase<any>;

  constructor() {
    const pgp = pgPromise();
    this.db = pgp({
      host: process.env.RDS_HOST,
      port: parseInt(process.env.RDS_PORT, 10),
      user: process.env.RDS_USER,
      password: process.env.RDS_PASSWORD,
      database: process.env.RDS_DATABASE,
    });
  }

  // Fetch data using a generic query
  async fetchFactData<T>(query: string, params: any[] = []): Promise<T[]> {
    logger.debug('Fetching data', { query, params });
    return this.db.any<T>(query, params);
  }

  // Prepare placeholders for dynamic query generation
  private preparePlaceholders(
    data: Record<string, any>,
    fieldTypes: {
      dateFields: string[];
      bitFields: string[];
      timestampFields: string[];
      uuidFields: string[];
      numericFields: string[];
    }
  ): string {
    const { dateFields, bitFields, timestampFields, uuidFields, numericFields } = fieldTypes;

    return Object.keys(data)
      .map((field, index) => {
        if (dateFields.includes(field)) return `$${index + 1}::DATE`;
        if (bitFields.includes(field)) return `$${index + 1}::BIT(1)`;
        if (timestampFields.includes(field)) return `$${index + 1}::TIMESTAMP`;
        if (uuidFields.includes(field)) return `$${index + 1}::UUID`;
        if (numericFields.includes(field)) return `$${index + 1}::NUMERIC(2,2)`;
        return `$${index + 1}`;
      })
      .join(', ');
  }

  // Create the ON condition for conflict resolution
  private prepareOnCondition(uniqueColumns: string[]): string {
    return uniqueColumns.map((column) => `target.${column} = source.${column}`).join(' AND ');
  }

  // Main mergeInstruments function without processData
  async mergeInstruments(
    tableName: string,
    dataList: Record<string, any>[],
    uniqueColumns: string[],
    fieldTypes: {
      dateFields: string[];
      bitFields: string[];
      timestampFields: string[];
      uuidFields: string[];
      numericFields: string[];
    }
  ): Promise<void> {
    if (!dataList.length) {
      logger.info('No data to merge');
      return;
    }

    // Prepare query components
    const firstRow = dataList[0];
    const columns = Object.keys(firstRow).join(', ');
    const placeholders = this.preparePlaceholders(firstRow, fieldTypes);
    const onCondition = this.prepareOnCondition(uniqueColumns);

    const query = `
      MERGE INTO ${tableName} AS target
      USING (VALUES (${placeholders})) AS source (${columns})
      ON ${onCondition}
      WHEN MATCHED THEN
        DO NOTHING
      WHEN NOT MATCHED THEN
        INSERT (${columns})
        VALUES (${columns.split(', ').map((col) => `source.${col}`).join(', ')});
    `;

    // Inline data processing instead of processData
    for (const row of dataList) {
      const values = Object.keys(row).map((field) => {
        if (fieldTypes.dateFields.includes(field)) {
          // Convert DATE fields to YYYY-MM-DD
          return row[field] instanceof Date
            ? (row[field] as Date).toISOString().split('T')[0]
            : row[field];
        }

        if (fieldTypes.bitFields.includes(field)) {
          // Validate BIT fields (0 or 1)
          return row[field] === '0' || row[field] === '1' ? row[field] : '0';
        }

        if (fieldTypes.timestampFields.includes(field)) {
          // Convert TIMESTAMP fields to ISO format
          return row[field] instanceof Date ? (row[field] as Date).toISOString() : row[field];
        }

        if (fieldTypes.uuidFields.includes(field)) {
          // Validate UUID format
          const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
          return uuidRegex.test(row[field]) ? row[field] : null;
        }

        if (fieldTypes.numericFields.includes(field)) {
          // Ensure precision for NUMERIC(2,2)
          return parseFloat(row[field]).toFixed(2);
        }

        return row[field]; // Default for other fields
      });
        logger.debug('Executing MERGE query', { query, values });

      try {
        await this.db.none(query, values);
      } catch (error) {
        logger.error('Error executing MERGE query', { error, row });
        throw error;
      }
    }

    logger.info('Merge operation completed successfully');
  }
}

export default FactService;
```

---

### Key Changes Without `processData`
1. **Inline Field Processing:**
   - The logic for handling `DATE`, `BIT`, `TIMESTAMP`, `UUID`, and `NUMERIC(2,2)` fields is directly included within the loop for processing `values`.

2. **Simplified Functionality:**
   - Removed `processData` entirely, making the validation logic embedded within the `mergeInstruments` loop.

3. **Loss of Centralization:**
   - The field-specific handling logic is now fragmented, making it harder to reuse or update. For example, adding a new field type would require modifying this loop.

---

### Pros Without `processData`
- **Simpler Code for Small Use Cases:**
  - Inline logic avoids the need for an additional function.
  - Useful for straightforward datasets with few special fields.

---

### Cons Without `processData`
- **Harder to Reuse:**
  - The logic is embedded within `mergeInstruments`, meaning it can't be reused for other methods or tables.

- **Less Readable:**
  - Processing logic for different fields is scattered, reducing readability and maintainability.

- **Difficult to Extend:**
  - Adding support for new field types requires modifying the loop every time, risking duplication of code.

---

### Recommendation
Removing `processData` simplifies the design but sacrifices scalability and maintainability. If you anticipate handling multiple field types or using similar logic for other operations, I recommend keeping the `processData` function for centralization and clarity. Let me know how you'd like to proceed!


Here’s a well-structured implementation of the `FactService` and handler that adheres to **SOLID principles** and promotes clean architecture. The code embraces modular design, dependency injection, and pure functions wherever applicable for TypeScript best practices.

---

### `FactService`

```typescript
import pgPromise, { IDatabase } from 'pg-promise';
import { ILogger } from '../logger';

interface SpecialFields {
  dateFields: string[];
  bitFields: string[];
  timestampFields: string[];
  timeFields: string[];
  uuidFields: string[];
  numericFields: string[];
}

class FactService {
  private db: IDatabase<any>;
  private logger: ILogger;

  constructor(db: IDatabase<any>, logger: ILogger) {
    this.db = db;
    this.logger = logger;
  }

  /**
   * Fetch data with a generic query
   * @param query SQL query string
   * @param params Query parameters
   */
  async fetchData<T>(query: string, params: any[] = []): Promise<T[]> {
    this.logger.debug('Fetching data', { query, params });
    return this.db.any<T>(query, params);
  }

  /**
   * Prepare placeholders dynamically for SQL query
   * @param data Single row of data
   * @param fieldTypes Field type mappings
   */
  preparePlaceholders(data: Record<string, any>, fieldTypes: SpecialFields): string {
    const { dateFields, bitFields, timestampFields, timeFields, uuidFields, numericFields } = fieldTypes;

    return Object.keys(data)
      .map((field, index) => {
        if (dateFields.includes(field)) return `$${index + 1}::DATE`;
        if (bitFields.includes(field)) return `$${index + 1}::BIT(1)`;
        if (timestampFields.includes(field)) return `$${index + 1}::TIMESTAMP`;
        if (timeFields.includes(field)) return `$${index + 1}::TIME`;
        if (uuidFields.includes(field)) return `$${index + 1}::UUID`;
        if (numericFields.includes(field)) return `$${index + 1}::NUMERIC(2,2)`;
        return `$${index + 1}`;
      })
      .join(', ');
  }

  /**
   * Prepare ON condition for conflict resolution in MERGE query
   * @param uniqueColumns Array of columns used for conflict detection
   */
  prepareOnCondition(uniqueColumns: string[]): string {
    return uniqueColumns.map((column) => `target.${column} = source.${column}`).join(' AND ');
  }

  /**
   * Execute a MERGE operation with dynamically generated query
   * @param tableName Target table name
   * @param dataList List of rows to merge
   * @param uniqueColumns Conflict resolution columns
   * @param fieldTypes Special field type mappings
   */
  async mergeRecords(
    tableName: string,
    dataList: Record<string, any>[],
    uniqueColumns: string[],
    fieldTypes: SpecialFields
  ): Promise<void> {
    if (!dataList.length) {
      this.logger.info('No data to merge');
      return;
    }

    const firstRow = dataList[0];
    const columns = Object.keys(firstRow).join(', ');
    const placeholders = this.preparePlaceholders(firstRow, fieldTypes);
    const onCondition = this.prepareOnCondition(uniqueColumns);

    const query = `
      MERGE INTO ${tableName} AS target
      USING (VALUES (${placeholders})) AS source (${columns})
      ON ${onCondition}
      WHEN MATCHED THEN
        DO NOTHING
      WHEN NOT MATCHED THEN
        INSERT (${columns})
        VALUES (${columns.split(', ').map((col) => `source.${col}`).join(', ')});
    `;

    for (const row of dataList) {
      const values = Object.values(row); // Use values directly
      try {
        this.logger.debug('Executing MERGE query', { query, values });
        await this.db.none(query, values);
      } catch (error) {
        this.logger.error('Error executing MERGE query', { error, row });
        throw error;
      }
    }

    this.logger.info('Merge operation completed successfully');
  }
}

export default FactService;
```

---

### Handler

The handler ensures separation of concerns by delegating the merging logic to the `FactService` and passing all dependencies via constructor injection.

```typescript
import FactService from './services/FactService';
import pgPromise from 'pg-promise';
import logger from './logger'; // Assume logger implements ILogger interface

export const handler = async (event: any): Promise<any> => {
  const { instruments } = event;

  if (!instruments || !instruments.length) {
    return {
      statusCode: 400,
      body: JSON.stringify({ error: 'No data provided' }),
    };
  }

  const pgp = pgPromise();
  const db = pgp({
    host: process.env.RDS_HOST,
    port: parseInt(process.env.RDS_PORT, 10),
    user: process.env.RDS_USER,
    password: process.env.RDS_PASSWORD,
    database: process.env.RDS_DATABASE,
  });

  const factService = new FactService(db, logger);

  // Special fields requiring type-specific handling
  const fieldTypes = {
    dateFields: ['trade_date', 'another_date_field'],
    bitFields: ['bit_field', 'another_bit_field'],
    timestampFields: ['timestamp_field', 'another_timestamp_field'],
    timeFields: ['start_time', 'end_time'], // TIME WITHOUT TIME ZONE fields
    uuidFields: ['uuid_field', 'another_uuid_field'],
    numericFields: ['numeric_field', 'another_numeric_field'],
  };

  try {
    // Call FactService to perform merge operation
    const tableName = 'instruments';
    const uniqueColumns = ['instrument_code', 'commodity_code'];

    await factService.mergeRecords(tableName, instruments, uniqueColumns, fieldTypes);

    return {
      statusCode: 200,
      body: JSON.stringify({ message: 'Merge operation completed successfully' }),
    };
  } catch (error) {
    logger.error('Handler Error', { error });

    return {
      statusCode: 500,
      body: JSON.stringify({
        error: 'Internal Server Error',
        details: error.message,
      }),
    };
  }
};
```

---

### SOLID Principles Applied

1. **Single Responsibility Principle (SRP):**
   - `FactService` handles all database-related logic (query generation and execution).
   - The handler focuses solely on handling HTTP requests and delegating business logic.

2. **Open-Closed Principle (OCP):**
   - The `FactService` can be extended to handle additional field types without modifying core logic, thanks to the `fieldTypes` mapping.

3. **Liskov Substitution Principle (LSP):**
   - The design ensures that `FactService` can be used as a substitute for similar services that adhere to the same interface or structure.

4. **Interface Segregation Principle (ISP):**
   - The `ILogger` interface defines specific logging operations, ensuring that the `FactService` doesn’t depend on unnecessary functionality.

5. **Dependency Inversion Principle (DIP):**
   - The `FactService` and handler depend on abstractions (`ILogger` for logging and `IDatabase` for database operations), promoting flexibility.

---

### Benefits of This Design
1. **Modular and Clean:**
   - Each class and method is focused on a single responsibility, making the code easier to maintain and extend.

2. **Reusable Logic:**
   - `FactService` can be reused across multiple handlers or projects with minimal changes.

3. **Pure Functions:**
   - Functions like `preparePlaceholders` and `prepareOnCondition` are pure and don't have any side effects.

4. **TypeScript Best Practices:**
   - Explicit interfaces and type annotations ensure clarity and safety in the codebase.

Let me know if you'd like further refinements or details!




# Rule Engine Wrapper Documentation

## Overview

The `RuleEngineWrapper` provides a simplified interface for working with rules, facts, and data storage by combining three core services:
- `FactService` - For fetching data from a PostgreSQL database
- `RuleService` - For evaluating business rules using json-rules-engine
- `S3Service` - For retrieving rule definitions from S3

## Installation

```bash
npm install your-package-name
```

## Initialization

```typescript
import RuleEngineWrapper from 'your-package-name';

// Initialize the wrapper (it will automatically initialize all underlying services)
const ruleEngine = new RuleEngineWrapper();
```

## Core Methods

### 1. Fetching Data (FactService)

#### `fetchFactData(query: string): Promise<T[]>`

Fetches data from the database using the provided SQL query.

**Example:**
```typescript
// Fetch market segments
const marketSegments = await ruleEngine.fetchFactData<MarketSegment>(
  `SELECT market_segment_code, calendar_id 
   FROM market_segment 
   WHERE start_date <= NOW() AND end_date >= NOW()`
);

console.log(marketSegments);
// Output: [{ market_segment_code: 'NYMEX', calendar_id: 1 }, ...]
```

### 2. Evaluating Rules (RuleService)

#### `evaluateRules(rules: Rule[], dynamicFacts: Record<string, DynamicFact>): Promise<Event[]>`

Evaluates a set of rules against dynamic facts.

**Example:**
```typescript
import { Rule } from 'json-rules-engine';

// Define rules
const rules = [
  new Rule({
    conditions: {
      all: [{
        fact: 'marketSegments',
        operator: 'greaterThan',
        value: 0,
        path: '$.length'
      }]
    },
    event: {
      type: 'marketSegmentsAvailable',
      params: { message: 'Market segments found' }
    }
  })
];

// Define dynamic facts
const dynamicFacts = {
  marketSegments: async () => {
    return await ruleEngine.fetchFactData(
      `SELECT market_segment_code FROM market_segment`
    );
  }
};

// Evaluate rules
const results = await ruleEngine.evaluateRules(rules, dynamicFacts);

console.log(results);
// Output: [{ type: 'marketSegmentsAvailable', params: { message: 'Market segments found' } }]
```

### 3. Fetching Rules from S3 (S3Service)

#### `fetchRulesFromS3<T>(bucket: string, key: string): Promise<T>`

Retrieves rule definitions from an S3 bucket.

**Example:**
```typescript
// Fetch rules from S3
const rules = await ruleEngine.fetchRulesFromS3<Rule[]>(
  'my-rules-bucket',
  'path/to/rules.json'
);

console.log(rules);
// Output: [ { conditions: { all: [...] }, event: { type: '...' } }, ... ]
```

## Complete Usage Example

Here's a complete example showing how to use all three services together:

```typescript
import RuleEngineWrapper from 'your-package-name';
import { Rule } from 'json-rules-engine';

async function runRuleEngine() {
  const ruleEngine = new RuleEngineWrapper();
  
  try {
    // 1. Fetch rules from S3
    const rules = await ruleEngine.fetchRulesFromS3<Rule[]>(
      'production-rules-bucket',
      'trading-rules/2023-10.json'
    );
    
    // 2. Define dynamic facts that will be evaluated against the rules
    const dynamicFacts = {
      marketSegments: async () => {
        return await ruleEngine.fetchFactData(
          `SELECT market_segment_code, calendar_id 
           FROM market_segment 
           WHERE start_date <= NOW() AND end_date >= NOW()`
        );
      },
      nextTradingDate: async (params: any, almanac: any) => {
        const segments = await almanac.factValue('marketSegments');
        const dates = await Promise.all(
          segments.map((segment: any) => 
            ruleEngine.fetchFactData(
              `SELECT MIN(trade_date) AS next_trade_date 
               FROM calendar 
               WHERE calendar_id = $1 
                 AND trade_date > NOW() 
                 AND trade_date_indicator = 'Y'`,
              [segment.calendar_id]
            )
          );
        return dates.flat();
      }
    };
    
    // 3. Evaluate the rules
    const results = await ruleEngine.evaluateRules(rules, dynamicFacts);
    
    console.log('Rule evaluation results:', results);
    return results;
    
  } catch (error) {
    console.error('Error in rule engine execution:', error);
    throw error;
  }
}

runRuleEngine();
```

## Error Handling

The wrapper will propagate errors from the underlying services. Always wrap calls in try/catch blocks:

```typescript
try {
  const data = await ruleEngine.fetchFactData('SELECT...');
  // ... other operations
} catch (error) {
  console.error('Failed to execute rule engine operation:', error);
  // Handle error appropriately
}
```

## Configuration

The wrapper uses environment variables for configuration. Ensure these are set before initialization:

```bash
# Database (FactService)
RDS_HOST=your-db-host
RDS_PORT=5432
RDS_USER=your-db-user
RDS_PASSWORD=your-db-password
RDS_DATABASE=your-db-name

# S3 (S3Service)
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-1
S3_ENDPOINT= # Optional for non-AWS S3
FORCE_PATH_STYLE=false # Set to true for localstack/minio
```

## Best Practices

1. **Reuse the wrapper instance** - Creating multiple instances is expensive
2. **Cache rules** - Fetch rules once at startup if they don't change often
3. **Keep queries simple** - Complex queries should be in stored procedures
4. **Monitor performance** - Large fact sets or complex rules can impact performance

## TypeScript Support

The wrapper is fully typed. You can provide type parameters for better type safety:

```typescript
interface MarketSegment {
  market_segment_code: string;
  calendar_id: number;
}

const segments = await ruleEngine.fetchFactData<MarketSegment>(
  'SELECT market_segment_code, calendar_id FROM market_segment'
);
// segments is now typed as MarketSegment[]
```


1. insertProcessedData<T>(query: string, values: T[]): Promise<T[]>
Executes a parameterized query to insert processed data into the database. This is ideal for bulk inserts or when you need precise control over the SQL statement.

Parameters:
query: Complete SQL query string with placeholders ($1, $2, etc.)

values: Array of values to be inserted (type-safe)

Returns:
Promise resolving to the inserted rows with their database-generated fields (like IDs)

// Insert processed instruments
const result = await ruleEngine.insertProcessedData<Instrument>(
  `INSERT INTO processed_instruments 
   (instrument_code, trade_date, price) 
   VALUES ($1, $2, $3) 
   RETURNING *`,
  [
    { instrument_code: 'CLZ23', trade_date: '2023-12-01', price: 75.42 },
    { instrument_code: 'HGZ23', trade_date: '2023-12-15', price: 3.85 }
  ]
);

console.log('Inserted:', result);
// Output: [{ id: 1, instrument_code: 'CLZ23', ...}, ...]



# AWS Credentials
AWS_ACCESS_KEY_ID=your_aws_access_key_id
AWS_SECRET_ACCESS_KEY=your_aws_secret_access_key
AWS_REGION=your_aws_region

# S3 Configuration
S3_BUCKET=your_s3_bucket_name
S3_KEYS=key1.json,key2.json,key3.json

# Database Configuration
RDS_HOST=your_rds_host
RDS_PORT=your_rds_port
RDS_USER=your_rds_username
RDS_PASSWORD=your_rds_password
RDS_DATABASE=your_rds_database_name


import { RuleEngineWrapper } from './services/RuleEngineWrapper';
import logger from './utils/logger';

const ruleEngineWrapper = new RuleEngineWrapper();

export const handler = async (event: any): Promise<any> => {
  try {
    // Step 1: Load bucket and keys from environment variables
    const bucket = process.env.S3_BUCKET!;
    const keys = process.env.S3_KEYS ? process.env.S3_KEYS.split(',') : [];
    if (!bucket || keys.length === 0) {
      throw new Error('S3_BUCKET and S3_KEYS environment variables must be defined');
    }

    logger.info('Fetching rules from S3', { bucket, keys });

    // Step 2: Fetch rules for each key in parallel
    const rulesPromises = keys.map(async (key) => {
      const ruleJson = await ruleEngineWrapper.fetchRulesFromS3(bucket, key);
      return { key, ruleJson }; // Create tuple/record with key and JSON file
    });

    const rules = await Promise.all(rulesPromises);

    logger.info('Rules fetched successfully', { rules });

    // Step 3: Pass rules into InstrumentProcessor
    const instrumentProcessor = new InstrumentProcessor(rules); // Assuming `InstrumentProcessor` accepts rules
    const instruments = await loadInstruments(); // Fetch instruments using InstrumentLoader
    const processedInstruments = await instrumentProcessor.processInstruments(instruments);

    logger.info('Instrument processing completed', { processedInstruments });

    return {
      statusCode: 200,
      body: JSON.stringify({ message: 'Processing completed successfully', processedInstruments }),
    };
  } catch (error) {
    logger.error('Error in handler', { error });
    return {
      statusCode: 500,
      body: JSON.stringify({ error: 'Internal Server Error', details: error.message }),
    };
  }
};




Here's the refactored implementation that first splits instruments by market segment, then processes each segment through the instrument processor, and finally combines and stores the results:

```typescript
import { Instrument, MarketSegment } from '../types/Instrument';
import { BusinessRule } from '../types/Rule';
import { FactService } from './FactService';
import { InstrumentProcessor } from './InstrumentProcessor';

interface ProcessingResult {
  validInstruments: Instrument[];
  invalidCount: number;
  segmentResults: Record<MarketSegment, SegmentResult>;
}

interface SegmentResult {
  count: number;
  firstSequence: number;
  lastSequence: number;
}

export class InstrumentPipeline {
  constructor(
    private factService: FactService,
    private rules: BusinessRule[]
  ) {}

  // 1. Split instruments by market segment
  private splitBySegment(instruments: Instrument[]): Record<MarketSegment, Instrument[]> {
    return instruments.reduce((acc, instrument) => {
      const segment = instrument.market_segment_code;
      if (!acc[segment]) {
        acc[segment] = [];
      }
      acc[segment].push(instrument);
      return acc;
    }, {} as Record<MarketSegment, Instrument[]>);
  }

  // 2. Process each segment through instrument processor
  private async processSegments(
    segments: Record<MarketSegment, Instrument[]>
  ): Promise<{
    allValid: Instrument[];
    segmentResults: Record<MarketSegment, SegmentResult>;
  }> {
    const segmentEntries = Object.entries(segments);
    const processor = new InstrumentProcessor(this.factService, this.rules);

    // Process all segments in parallel
    const processedSegments = await Promise.all(
      segmentEntries.map(async ([segment, instruments]) => {
        const result = await processor.processInstruments(instruments);
        return { segment, result };
      })
    );

    // Combine results
    const allValid: Instrument[] = [];
    const segmentResults: Record<MarketSegment, SegmentResult> = {};

    processedSegments.forEach(({ segment, result }) => {
      allValid.push(...result.validInstruments);
      segmentResults[segment] = {
        count: result.validInstruments.length,
        firstSequence: result.validInstruments[0]?.sequence_number || 0,
        lastSequence: result.validInstruments[result.validInstruments.length - 1]?.sequence_number || 0
      };
    });

    return { allValid, segmentResults };
  }

  // 3. Main processing pipeline
  async execute(rawInstruments: Instrument[]): Promise<ProcessingResult> {
    // Step 1: Split by market segment
    const segments = this.splitBySegment(rawInstruments);
    console.log(`Split into ${Object.keys(segments).length} market segments`);

    // Step 2: Process each segment
    const { allValid, segmentResults } = await this.processSegments(segments);

    // Step 3: Apply final global rules
    const finalInstruments = this.applyGlobalRules(allValid);
    console.log(`Final valid instruments: ${finalInstruments.length}`);

    // Step 4: Store results
    await this.factService.batchInsertInstruments(finalInstruments);

    return {
      validInstruments: finalInstruments,
      invalidCount: rawInstruments.length - finalInstruments.length,
      segmentResults
    };
  }

  private applyGlobalRules(instruments: Instrument[]): Instrument[] {
    // Implement any cross-segment rules here
    return instruments.filter(instrument => 
      instrument.sequence_number <= 1000 // Example global limit
    );
  }
}
```

### InstrumentProcessor (Updated)

```typescript
export class InstrumentProcessor {
  constructor(
    private factService: FactService,
    private rules: BusinessRule[]
  ) {}

  async processInstruments(instruments: Instrument[]): Promise<{
    validInstruments: Instrument[];
    invalidCount: number;
  }> {
    if (instruments.length === 0) {
      return { validInstruments: [], invalidCount: 0 };
    }

    // 1. Apply business rules
    const filtered = this.applyRules(instruments);
    
    // 2. Assign sequence numbers
    const sequenced = this.assignSequenceNumbers(filtered);
    
    return {
      validInstruments: sequenced,
      invalidCount: instruments.length - sequenced.length
    };
  }

  private applyRules(instruments: Instrument[]): Instrument[] {
    return instruments.filter(instrument => 
      this.rules.every(rule => this.evaluateRule(instrument, rule))
    );
  }

  private evaluateRule(instrument: Instrument, rule: BusinessRule): boolean {
    // Implement rule evaluation logic
    switch (rule.type) {
      case 'active_only': 
        return instrument.is_active;
      case 'valid_dates':
        return new Date() >= new Date(instrument.first_trading_date) && 
               new Date() <= new Date(instrument.last_trading_date);
      default:
        return true;
    }
  }

  private assignSequenceNumbers(instruments: Instrument[]): Instrument[] {
    return instruments.map((instrument, index) => ({
      ...instrument,
      sequence_number: index + 1,
      processed_at: new Date()
    }));
  }
}
```

### Usage Example

```typescript
// Initialize
const factService = new FactService();
const rules = await loadRulesFromS3(); // Your rule loading implementation
const pipeline = new InstrumentPipeline(factService, rules);

// Execute pipeline
const rawInstruments = await factService.fetchActiveInstruments();
const results = await pipeline.execute(rawInstruments);

console.log('Processing complete');
console.log(`Valid instruments: ${results.validInstruments.length}`);
console.log(`Invalid instruments: ${results.invalidCount}`);
console.log('Segment results:', results.segmentResults);
```


Here's the full set of test cases for the `RuleHandler`, `InstrumentProcessor`, `SequentialGroupingTagging`, and `Lambda Handler`. These tests are written in a modular way, using Jest as the testing framework for easy execution.

---

### **1. Tests for RuleHandler**
This validates individual rule handlers and their chaining functionality.

```typescript
// tests/RuleHandler.test.ts
import { RuleHandler } from '../handlers/RuleHandler';
import { Rule1Handler } from '../handlers/Rule1Handler';
import { Rule2Handler } from '../handlers/Rule2Handler';

describe('RuleHandler', () => {
  let rule1Handler: Rule1Handler;
  let rule2Handler: Rule2Handler;

  beforeEach(() => {
    rule1Handler = new Rule1Handler();
    rule2Handler = new Rule2Handler();
    rule1Handler.setNext(rule2Handler);
  });

  it('should process an instrument that passes all rules', async () => {
    const instrument = { id: '1', rule1Condition: true, rule2Condition: true };
    const result = await rule1Handler.handle(instrument);
    expect(result).toEqual(instrument);
  });

  it('should stop processing if Rule 1 fails', async () => {
    const instrument = { id: '2', rule1Condition: false, rule2Condition: true };
    await expect(rule1Handler.handle(instrument)).rejects.toThrow('Instrument 2 failed Rule 1');
  });

  it('should stop processing if Rule 2 fails', async () => {
    const instrument = { id: '3', rule1Condition: true, rule2Condition: false };
    await expect(rule1Handler.handle(instrument)).rejects.toThrow('Instrument 3 failed Rule 2');
  });
});
```

---

### **2. Tests for InstrumentProcessor**
This tests the overall processing of instruments using the rule chain.

```typescript
// tests/InstrumentProcessor.test.ts
import { InstrumentProcessor } from '../processors/InstrumentProcessor';
import { Rule1Handler } from '../handlers/Rule1Handler';
import { Rule2Handler } from '../handlers/Rule2Handler';

describe('InstrumentProcessor', () => {
  let rule1Handler: Rule1Handler;
  let rule2Handler: Rule2Handler;
  let instrumentProcessor: InstrumentProcessor;

  beforeEach(() => {
    rule1Handler = new Rule1Handler();
    rule2Handler = new Rule2Handler();
    rule1Handler.setNext(rule2Handler);

    instrumentProcessor = new InstrumentProcessor(rule1Handler);
  });

  it('should process only valid instruments', async () => {
    const instruments = [
      { id: '1', rule1Condition: true, rule2Condition: true },
      { id: '2', rule1Condition: false, rule2Condition: true },
      { id: '3', rule1Condition: true, rule2Condition: false },
      { id: '4', rule1Condition: true, rule2Condition: true },
    ];

    const result = await instrumentProcessor.processInstruments(instruments);

    expect(result).toEqual([
      { id: '1', rule1Condition: true, rule2Condition: true },
      { id: '4', rule1Condition: true, rule2Condition: true },
    ]);
  });

  it('should return an empty array if no instruments are valid', async () => {
    const instruments = [
      { id: '1', rule1Condition: false, rule2Condition: true },
      { id: '2', rule1Condition: true, rule2Condition: false },
    ];

    const result = await instrumentProcessor.processInstruments(instruments);

    expect(result).toEqual([]);
  });
});
```

---

### **3. Tests for SequentialGroupingTagging**
This validates the grouping and sequence assignment logic.

```typescript
// tests/SequentialGroupingTagging.test.ts
import { assignSequenceNumbersByGroup } from '../utils/SequencingUtil';

describe('SequentialGroupingTagging', () => {
  it('should assign sequence numbers based on groups', () => {
    const instruments = [
      { id: '1', trade_sub_type: 'block', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-12-01' },
      { id: '2', trade_sub_type: 'block', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-12-01' },
      { id: '3', trade_sub_type: 'block', commodity_code: 'silver', instrument_type: 'type2', expiry_date: '2025-11-01' },
      { id: '4', trade_sub_type: 'efp', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-10-01' },
    ];

    const result = assignSequenceNumbersByGroup(instruments);

    expect(result).toEqual([
      { id: '1', trade_sub_type: 'block', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-12-01', sequence_number: 1 },
      { id: '2', trade_sub_type: 'block', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-12-01', sequence_number: 1 },
      { id: '3', trade_sub_type: 'block', commodity_code: 'silver', instrument_type: 'type2', expiry_date: '2025-11-01', sequence_number: 2 },
      { id: '4', trade_sub_type: 'efp', commodity_code: 'gold', instrument_type: 'type1', expiry_date: '2025-10-01', sequence_number: 3 },
    ]);
  });

  it('should handle an empty instrument list', () => {
    const instruments: any[] = [];
    const result = assignSequenceNumbersByGroup(instruments);
    expect(result).toEqual([]);
  });
});
```

---

### **4. Tests for Lambda Handler**
This tests the full workflow of the Lambda handler, including integration of all components.

```typescript
// tests/LambdaHandler.test.ts
import { handler } from '../lambda/Handler';
import { InstrumentLoader } from '../loaders/InstrumentLoader';
import { DatabaseStorageHandler } from '../utils/DatabaseStorageHandler';
import { assignSequenceNumbersByGroup } from '../utils/SequencingUtil';

jest.mock('../loaders/InstrumentLoader');
jest.mock('../utils/DatabaseStorageHandler');
jest.mock('../utils/SequencingUtil');

describe('Lambda Handler', () => {
  let mockInstruments: any[];

  beforeEach(() => {
    mockInstruments = [
      { id: '1', rule1Condition: true, rule2Condition: true },
      { id: '2', rule1Condition: false, rule2Condition: true },
      { id: '3', rule1Condition: true, rule2Condition: false },
      { id: '4', rule1Condition: true, rule2Condition: true },
    ];

    (InstrumentLoader.prototype.loadInstruments as jest.Mock).mockResolvedValue(mockInstruments);
    (DatabaseStorageHandler.prototype.storeInstruments as jest.Mock).mockResolvedValue(undefined);
    (assignSequenceNumbersByGroup as jest.Mock).mockImplementation((instruments) => instruments.map((i, index) => ({ ...i, sequence_number: index + 1 })));
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should process and store valid instruments', async () => {
    const event = {};
    const response = await handler(event);

    expect(InstrumentLoader.prototype.loadInstruments).toHaveBeenCalled();
    expect(DatabaseStorageHandler.prototype.storeInstruments).toHaveBeenCalledWith([
      { id: '1', rule1Condition: true, rule2Condition: true, sequence_number: 1 },
      { id: '4', rule1Condition: true, rule2Condition: true, sequence_number: 2 },
    ]);
    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body).message).toBe('Processing complete');
  });

  it('should return 200 and empty array if no instruments are valid', async () => {
    (InstrumentLoader.prototype.loadInstruments as jest.Mock).mockResolvedValue([]);

    const event = {};
    const response = await handler(event);

    expect(InstrumentLoader.prototype.loadInstruments).toHaveBeenCalled();
    expect(DatabaseStorageHandler.prototype.storeInstruments).not.toHaveBeenCalled();
    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body).validInstruments).toEqual([]);
  });

  it('should return 500 if an error occurs', async () => {
    (InstrumentLoader.prototype.loadInstruments as jest.Mock).mockRejectedValue(new Error('Database error'));

    const event = {};
    const response = await handler(event);

    expect(response.statusCode).toBe(500);
    expect(JSON.parse(response.body).error).toBe('Internal Server Error');
  });
});
```

---

### **Execution**

To run the tests, use the following command:

```bash
npx jest
```

---

### **Key Features of These Tests**
1. **Unit Tests**:
   - Validates individual components like `RuleHandler`, `InstrumentProcessor`, and `SequentialGroupingTagging`.
2. **Integration Tests**:
   - Ensures all components work together seamlessly in the Lambda handler.
3. **Mocked Dependencies**:
   - External dependencies like `InstrumentLoader` and `DatabaseStorageHandler` are mocked for isolated testing.

Let me know if you'd like additional enhancements or scenarios added! 🚀



Here’s the complete implementation of the custom date operators for `json-rules-engine`, integration into `RuleEngineWrapper`, and associated test cases. This structure ensures clarity, modularity, and maintainability.

---

### **1. Date Operators Implementation**
Add custom date operators to handle comparisons like `lessThan`, `greaterThan`, `lessThanInclusive`, `greaterThanInclusive`, `equal`, and `notEqual`.

#### **DateOperators.ts**
```typescript
import { Engine } from 'json-rules-engine';

export const addDateOperators = (engine: Engine) => {
  engine.addOperator('dateLessThan', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue) < new Date(jsonValue);
  });

  engine.addOperator('dateLessThanInclusive', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue) <= new Date(jsonValue);
  });

  engine.addOperator('dateGreaterThan', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue) > new Date(jsonValue);
  });

  engine.addOperator('dateGreaterThanInclusive', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue) >= new Date(jsonValue);
  });

  engine.addOperator('dateEqual', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue).getTime() === new Date(jsonValue).getTime();
  });

  engine.addOperator('dateNotEqual', (factValue: string | Date, jsonValue: string | Date) => {
    return new Date(factValue).getTime() !== new Date(jsonValue).getTime();
  });
};
```

---

### **2. Integration with RuleEngineWrapper**
Modify the `RuleEngineWrapper` to include the custom date operators.

#### **RuleEngineWrapper.ts**
```typescript
import { Engine, Rule, Event } from 'json-rules-engine';
import { addDateOperators } from './DateOperators';

class RuleEngineWrapper {
  private engine: Engine;

  constructor() {
    this.engine = new Engine();
    addDateOperators(this.engine); // Add custom date operators
  }

  addRules(rules: Rule[]): void {
    for (const rule of rules) {
      this.engine.addRule(rule);
    }
  }

  async evaluateRules(facts: Record<string, any>): Promise<Event[]> {
    const { events } = await this.engine.run(facts);
    return events;
  }
}

export default RuleEngineWrapper;
```

---

### **3. Sample Rules**
Here’s a set of rules utilizing the custom date operators:

#### **rules.json**
```json
[
  {
    "conditions": {
      "all": [
        {
          "fact": "nextTradingDate",
          "operator": "dateGreaterThan",
          "value": "2025-04-01"
        },
        {
          "fact": "firstTradingDate",
          "operator": "dateLessThanInclusive",
          "value": "2025-05-01"
        }
      ]
    },
    "event": {
      "type": "dateComparison",
      "params": {
        "message": "Date conditions met successfully"
      }
    }
  }
]
```

---

### **4. Test Cases**
Write unit tests for the custom date operators and their integration into `RuleEngineWrapper`.

#### **DateOperators.test.ts**
```typescript
import { Engine } from 'json-rules-engine';
import { addDateOperators } from '../DateOperators';

describe('Date Operators', () => {
  let engine: Engine;

  beforeEach(() => {
    engine = new Engine();
    addDateOperators(engine);
  });

  it('should evaluate dateLessThan correctly', async () => {
    const rule = {
      conditions: {
        all: [
          { fact: 'testDate', operator: 'dateLessThan', value: '2025-04-01' },
        ],
      },
      event: { type: 'dateComparison', params: { message: 'Success' } },
    };

    engine.addRule(rule);

    const { events } = await engine.run({ testDate: '2025-03-01' });

    expect(events).toHaveLength(1);
    expect(events[0].params.message).toBe('Success');
  });

  it('should evaluate dateGreaterThanInclusive correctly', async () => {
    const rule = {
      conditions: {
        all: [
          { fact: 'testDate', operator: 'dateGreaterThanInclusive', value: '2025-04-01' },
        ],
      },
      event: { type: 'dateComparison', params: { message: 'Success' } },
    };

    engine.addRule(rule);

    const { events } = await engine.run({ testDate: '2025-04-01' });

    expect(events).toHaveLength(1);
    expect(events[0].params.message).toBe('Success');
  });

  it('should evaluate dateEqual correctly', async () => {
    const rule = {
      conditions: {
        all: [
          { fact: 'testDate', operator: 'dateEqual', value: '2025-04-01' },
        ],
      },
      event: { type: 'dateComparison', params: { message: 'Success' } },
    };

    engine.addRule(rule);

    const { events } = await engine.run({ testDate: '2025-04-01' });

    expect(events).toHaveLength(1);
    expect(events[0].params.message).toBe('Success');
  });

  it('should evaluate dateNotEqual correctly', async () => {
    const rule = {
      conditions: {
        all: [
          { fact: 'testDate', operator: 'dateNotEqual', value: '2025-04-01' },
        ],
      },
      event: { type: 'dateComparison', params: { message: 'Success' } },
    };

    engine.addRule(rule);

    const { events } = await engine.run({ testDate: '2025-04-02' });

    expect(events).toHaveLength(1);
    expect(events[0].params.message).toBe('Success');
  });
});
```
Here's the complete simplified `FactService` code that removes UUID-specific handling since PostgreSQL can handle UUIDs directly:

```typescript
import pgPromise from 'pg-promise';
import logger from '../logger';

type SpecialFields = {
  dateFields?: string[];
  timeFields?: string[];
  timestampFields?: string[];
  numericFields?: string[];
  jsonFields?: string[];
  booleanFields?: string[];
};

type MergeResult = {
  successCount: number;
  errorCount: number;
  errorDetails: { data: Record<string, any>; error: string }[];
};

class FactService {
  private db: pgPromise.IDatabase<any>;
  private pgp: pgPromise.IMain;

  constructor() {
    this.pgp = pgPromise({
      capSQL: true,
      query: (e) => logger.debug(`Executing query: ${e.query}`)
    });

    this.db = this.pgp({
      host: process.env.DB_HOST,
      port: parseInt(process.env.DB_PORT || '5432'),
      database: process.env.DB_NAME,
      user: process.env.DB_USER,
      password: process.env.DB_PASSWORD,
      max: 20
    });
  }

  private processValue(value: any, fieldType?: string): any {
    if (value === null || value === undefined) return null;

    switch (fieldType) {
      case 'date':
        return new Date(value).toISOString().split('T')[0];
      case 'time':
        return new Date(value).toISOString().split('T')[1].slice(0, 8);
      case 'timestamp':
        return new Date(value).toISOString();
      case 'numeric':
        const num = Number(value);
        return isNaN(num) ? 0 : num;
      case 'json':
        return typeof value === 'string' ? value : JSON.stringify(value);
      case 'boolean':
        return Boolean(value);
      default:
        return value;
    }
  }

  private processData(data: Record<string, any>, specialFields: SpecialFields): any[] {
    return Object.entries(data).map(([key, value]) => {
      if (specialFields.dateFields?.includes(key)) {
        return this.processValue(value, 'date');
      }
      if (specialFields.timeFields?.includes(key)) {
        return this.processValue(value, 'time');
      }
      if (specialFields.timestampFields?.includes(key)) {
        return this.processValue(value, 'timestamp');
      }
      if (specialFields.numericFields?.includes(key)) {
        return this.processValue(value, 'numeric');
      }
      if (specialFields.jsonFields?.includes(key)) {
        return this.processValue(value, 'json');
      }
      if (specialFields.booleanFields?.includes(key)) {
        return this.processValue(value, 'boolean');
      }
      return value;
    });
  }

  async insertSingle(
    tableName: string,
    data: Record<string, any>,
    specialFields: SpecialFields = {},
    options: { timeout?: number } = {}
  ): Promise<any> {
    const columns = Object.keys(data).join(', ');
    const values = this.processData(data, specialFields);
    const placeholders = values.map((_, i) => `$${i+1}`).join(', ');

    const query = `INSERT INTO ${tableName} (${columns}) VALUES (${placeholders}) RETURNING *`;
    
    try {
      const [result] = await this.db.any(query, values);
      return result;
    } catch (error) {
      logger.error('Insert failed', { error, tableName, data });
      throw error;
    }
  }

  async mergeBulk(
    tableName: string,
    dataList: Record<string, any>[],
    conflictColumns: string[],
    specialFields: SpecialFields = {},
    options: { timeout?: number } = {}
  ): Promise<MergeResult> {
    if (!dataList.length) {
      return { successCount: 0, errorCount: 0, errorDetails: [] };
    }

    const columns = Object.keys(dataList[0]);
    const values = dataList.flatMap(data => this.processData(data, specialFields));

    const cs = new this.pgp.helpers.ColumnSet(columns, { table: tableName });
    const query = `${this.pgp.helpers.insert(dataList, cs)} 
      ON CONFLICT (${conflictColumns.join(', ')}) 
      DO UPDATE SET ${columns.map(c => `${c}=EXCLUDED.${c}`).join(', ')} 
      RETURNING *`;

    try {
      const result = await this.db.any(query, values);
      return {
        successCount: result.length,
        errorCount: dataList.length - result.length,
        errorDetails: []
      };
    } catch (error) {
      logger.error('Bulk merge failed', { error, tableName });
      return {
        successCount: 0,
        errorCount: dataList.length,
        errorDetails: dataList.map(data => ({
          data,
          error: (error as Error).message
        }))
      };
    }
  }

  async close(): Promise<void> {
    await this.pgp.end();
  }
}

export default FactService;
```To dynamically derive concurrency for the `InstrumentProcessor` based on system resources, you can calculate the number of available CPU cores and adjust concurrency limits accordingly. For example, you can set the concurrency proportional to the number of CPU cores while applying an upper limit to avoid resource exhaustion.

Here's how you can implement this dynamically:

---

### Updated `InstrumentProcessor` Implementation with Dynamic Concurrency
```typescript
import Bottleneck from "bottleneck";
import os from "os";
import { RuleHandler } from "../handlers/RuleHandler";

export class InstrumentProcessor {
  private ruleHandlerChain: RuleHandler;
  private ruleEngineWrapper: any;
  private limiter: Bottleneck;

  constructor(ruleHandlerChain: RuleHandler, ruleEngineWrapper: any) {
    this.ruleHandlerChain = ruleHandlerChain;
    this.ruleEngineWrapper = ruleEngineWrapper;

    // Dynamically calculate max concurrency based on system resources
    const maxConcurrency = this.calculateMaxConcurrency();

    // Initialize Bottleneck with dynamic concurrency
    this.limiter = new Bottleneck({
      maxConcurrent: maxConcurrency,
      minTime: 0, // No delay between tasks
    });
  }

  /**
   * Dynamically calculate the maximum concurrency based on system resources.
   * @returns The max concurrency value.
   */
  private calculateMaxConcurrency(): number {
    const cpuCount = os.cpus().length; // Number of available CPU cores
    const hardLimit = 100; // Set a hard upper limit to avoid resource exhaustion
    const multiplier = 2; // Adjust multiplier for lightweight tasks
    return Math.min(cpuCount * multiplier, hardLimit); // Derive concurrency dynamically
  }

  /**
   * Processes a list of instruments with concurrency control.
   * @param instruments List of instruments to process.
   * @returns The processed instruments.
   */
  async processInstruments(instruments: any[]): Promise<any[]> {
    const processedInstruments: any[] = [];

    // Schedule each instrument for processing
    const tasks = instruments.map((instrument) =>
      this.limiter.schedule(async () => {
        try {
          // Process the instrument using the rule handler chain
          const result = await this.ruleHandlerChain.handle(instrument);

          // Reset the rule engine wrapper after processing
          this.ruleEngineWrapper.reset();

          // Add the result to the processed instruments if valid
          if (result) {
            processedInstruments.push(result);
          }
        } catch (error) {
          console.error(`Error processing instrument: ${JSON.stringify(instrument)}`, error);
        }
      })
    );

    // Wait for all tasks to complete
    await Promise.all(tasks);

    return processedInstruments;
  }
}
```

---

### Key Features:
1. **Dynamic Concurrency Calculation:**
   - Uses `os.cpus().length` to dynamically derive the concurrency based on the system's available CPU cores.
   - Multiplies the CPU core count by a configurable multiplier (e.g., `2`) to increase concurrency for lightweight tasks.
   - Applies a hard limit (e.g., `100`) to prevent resource exhaustion in systems with a large number of CPU cores.

2. **Flexible Concurrency Control:**
   - Bottleneck automatically limits concurrent execution based on the derived value.

3. **Robust Error Handling:**
   - Errors are caught at the task level to ensure the process continues even if some tasks fail.

4. **Efficient Resource Utilization:**
   - The dynamically derived concurrency ensures the processor optimally utilizes system resources without overwhelming the CPU.

---

### Example Usage:
```typescript
import { InstrumentProcessor } from "./InstrumentProcessor";
import { RuleHandler } from "../handlers/RuleHandler";

// Example RuleHandler (dummy implementation for illustration)
const ruleHandler = new RuleHandler();
const ruleEngineWrapper = {
  reset: () => console.log("Rule Engine Reset"),
};

// Instantiate InstrumentProcessor (concurrency derived dynamically)
const processor = new InstrumentProcessor(ruleHandler, ruleEngineWrapper);

// List of instruments to process (sample data)
const instruments = [
  { id: 1, name: "Instrument A" },
  { id: 2, name: "Instrument B" },
  { id: 3, name: "Instrument C" },
  { id: 4, name: "Instrument D" },
  { id: 5, name: "Instrument E" },
];

// Process instruments
(async () => {
  const processedInstruments = await processor.processInstruments(instruments);
  console.log("Processed Instruments:", processedInstruments);
})();
```

---

### Monitoring Performance:
After implementing this solution, you can test the system under varying workloads to ensure that the dynamically calculated concurrency performs optimally:
1. **CPU Utilization:** Monitor CPU usage during execution using tools like `htop` or Task Manager.
2. **Execution Time:** Measure the time taken to process all instruments and compare it across different multipliers.
3. **Adjust Multiplier:** Fine-tune the multiplier (`2`) in `calculateMaxConcurrency` based on task complexity and performance.

Let me know if you need help with further refinements or additional features!






✅ Complete code is now organized into separate classes with helpful comments:

### ✅ Test File
- `Rule-engine-trade-tests`: Contains all scenarios and `runRuleTest` logic.

### ✅ Core Rule Engine Wrapper
- `Rule-engine-wrapper`: Handles dynamic or inline rule execution and integrates with services.

### ✅ Support Services
- `Fact-and-rule-services`: Simulates `FactService` and `RuleService` for DB or S3-backed data.

Let me know if you'd like:
- Real `pg-promise` integration for `FactService`
- Auto-loading JSON rules from S3
- CLI script to run tests or rules interactively



Sure! Here are the **source file** and **test file** for your array operators using `json-rules-engine`.

---

### **`arrayOperators.ts` (Source File)**
```typescript
import { Engine } from 'json-rules-engine';

export const addArrayOperators = (engine: Engine) => {
  // Check if two arrays are strictly equal (same values, same order)
  engine.addOperator('arrayEqual', (factValue: any[], jsonValue: any[]) => {
    if (!Array.isArray(factValue) || !Array.isArray(jsonValue)) return false;
    return factValue.length === jsonValue.length && factValue.every((val, index) => val === jsonValue[index]);
  });

  // Check if two arrays contain the same elements (ignoring order)
  engine.addOperator('arraySameElements', (factValue: any[], jsonValue: any[]) => {
    if (!Array.isArray(factValue) || !Array.isArray(jsonValue)) return false;
    return factValue.length === jsonValue.length && factValue.sort().toString() === jsonValue.sort().toString();
  });

  // Check if fact array contains all elements of json array
  engine.addOperator('arrayContainsAll', (factValue: any[], jsonValue: any[]) => {
    if (!Array.isArray(factValue) || !Array.isArray(jsonValue)) return false;
    return jsonValue.every(val => factValue.includes(val));
  });

  // Check if fact array contains any element of json array
  engine.addOperator('arrayContainsAny', (factValue: any[], jsonValue: any[]) => {
    if (!Array.isArray(factValue) || !Array.isArray(jsonValue)) return false;
    return jsonValue.some(val => factValue.includes(val));
  });

  // Check if arrays have overlapping elements
  engine.addOperator('arrayIntersects', (factValue: any[], jsonValue: any[]) => {
    if (!Array.isArray(factValue) || !Array.isArray(jsonValue)) return false;
    return factValue.some(val => jsonValue.includes(val));
  });
};
```

---

### **`arrayOperators.test.ts` (Test File)**
```typescript
import { Engine } from 'json-rules-engine';
import { addArrayOperators } from './arrayOperators';

describe('Array Custom Operators', () => {
  let engine: Engine;

  beforeEach(() => {
    engine = new Engine();
    addArrayOperators(engine);
  });

  test('arrayEqual - should return true for identical arrays', async () => {
    const facts = { arrayFact: [1, 2, 3] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayEqual', value: [1, 2, 3] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });

  test('arrayEqual - should return false for different arrays', async () => {
    const facts = { arrayFact: [1, 2, 3] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayEqual', value: [3, 2, 1] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(0);
  });

  test('arraySameElements - should return true for same elements ignoring order', async () => {
    const facts = { arrayFact: ['a', 'b', 'c'] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arraySameElements', value: ['b', 'c', 'a'] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });

  test('arrayContainsAll - should return true when fact array contains all elements of rule array', async () => {
    const facts = { arrayFact: [1, 2, 3, 4, 5] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayContainsAll', value: [2, 3] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });

  test('arrayContainsAny - should return true when fact array contains at least one element of rule array', async () => {
    const facts = { arrayFact: [10, 20, 30, 40] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayContainsAny', value: [30, 99] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });

  test('arrayIntersects - should return true when arrays share at least one element', async () => {
    const facts = { arrayFact: ['x', 'y', 'z'] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayIntersects', value: ['y', 'q'] }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });
});
```

---

### **Next Steps:**
1. **Run the tests** using:
   ```bash
   npm test
   ```
2. **Ensure you have dependencies installed**, if needed:
   ```bash
   npm install --save-dev jest ts-jest @types/jest
   ```

This setup will validate all array operators efficiently. Let me know if you need refinements or optimizations! 🚀



import { Engine } from 'json-rules-engine';

export const addArrayOperators = (engine: Engine) => {
  // Ensure all values in an array are the same
  engine.addOperator('arrayAllSame', (factValue: any[]) => {
    if (!Array.isArray(factValue) || factValue.length === 0) return false;
    return new Set(factValue).size === 1;
  });

  // Existing operators...
};
import { Engine } from 'json-rules-engine';
import { addArrayOperators } from './arrayOperators';

describe('Array Custom Operators', () => {
  let engine: Engine;

  beforeEach(() => {
    engine = new Engine();
    addArrayOperators(engine);
  });

  test('arrayAllSame - should return true when all elements are identical', async () => {
    const facts = { arrayFact: ["SEG1", "SEG1", "SEG1"] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayAllSame' }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1);
  });

  test('arrayAllSame - should return false when elements are different', async () => {
    const facts = { arrayFact: ["SEG1", "SEG2", "SEG1"] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayAllSame' }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(0);
  });

  test('arrayAllSame - should return false for an empty array', async () => {
    const facts = { arrayFact: [] };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayAllSame' }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(0);
  });

  test('arrayAllSame - should return false if factValue is not an array', async () => {
    const facts = { arrayFact: "SEG1" };
    const rule = {
      conditions: { any: [{ fact: 'arrayFact', operator: 'arrayAllSame' }] },
      event: { type: 'success' },
    };
    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(0);
  });
});




Here's the **JSON rule** and **Jest test cases** to validate that:
1. **Each trade leg's buyer firm exists in the firm list** (`arrayContainsAny`).
2. **The firm's market segment matches the trade leg's market segment** (`arrayContainsAll`).

---

### **JSON Rule**
```json
{
  "conditions": {
    "all": [
      {
        "fact": "trade",
        "operator": "arrayContainsAny",
        "path": "$.legs[*].buyer_firm",
        "value": { "fact": "firms", "path": "$[*].firm_code" }
      },
      {
        "fact": "trade",
        "operator": "arrayContainsAll",
        "path": "$.legs[*].market_segment",
        "value": {
          "fact": "firms",
          "path": "$[?(@.firm_code in trade.legs[*].buyer_firm)].market_segment"
        }
      }
    ]
  },
  "event": {
    "type": "marketSegmentValidationSuccess",
    "params": {
      "message": "Each trade leg's buyer firm exists and has a valid market segment."
    }
  }
}
```

---

### **Jest Test Cases (`tradeValidation.test.ts`)**
```typescript
import { Engine } from 'json-rules-engine';
import { addArrayOperators } from './arrayOperators';

describe('Trade Leg Buyer Firm and Market Segment Validation', () => {
  let engine: Engine;

  beforeEach(() => {
    engine = new Engine();
    addArrayOperators(engine);
  });

  test('should pass when each buyer firm exists and matches the market segment', async () => {
    const facts = {
      trade: {
        legs: [
          { buyer_firm: "JPM_AU", market_segment: "AU" },
          { buyer_firm: "BNZ_NZ", market_segment: "NZ" }
        ]
      },
      firms: [
        { firm_code: "JPM_AU", market_segment: "AU" },
        { firm_code: "BNZ_NZ", market_segment: "NZ" }
      ]
    };

    const rule = {
      conditions: {
        all: [
          {
            fact: "trade",
            operator: "arrayContainsAny",
            path: "$.legs[*].buyer_firm",
            value: { fact: "firms", path: "$[*].firm_code" }
          },
          {
            fact: "trade",
            operator: "arrayContainsAll",
            path: "$.legs[*].market_segment",
            value: {
              fact: "firms",
              path: "$[?(@.firm_code in trade.legs[*].buyer_firm)].market_segment"
            }
          }
        ]
      },
      event: { type: "marketSegmentValidationSuccess" }
    };

    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(1); // ✅ Should pass
  });

  test('should fail when a buyer firm exists but has a different market segment', async () => {
    const facts = {
      trade: {
        legs: [
          { buyer_firm: "JPM_AU", market_segment: "AU" },
          { buyer_firm: "BNZ_AU", market_segment: "NZ" } // ❌ Incorrect mapping
        ]
      },
      firms: [
        { firm_code: "JPM_AU", market_segment: "AU" },
        { firm_code: "BNZ_AU", market_segment: "AU" } // ❌ Missing "NZ"
      ]
    };

    const rule = {
      conditions: {
        all: [
          {
            fact: "trade",
            operator: "arrayContainsAny",
            path: "$.legs[*].buyer_firm",
            value: { fact: "firms", path: "$[*].firm_code" }
          },
          {
            fact: "trade",
            operator: "arrayContainsAll",
            path: "$.legs[*].market_segment",
            value: {
              fact: "firms",
              path: "$[?(@.firm_code in trade.legs[*].buyer_firm)].market_segment"
            }
          }
        ]
      },
      event: { type: "marketSegmentValidationSuccess" }
    };

    engine.addRule(rule);
    const results = await engine.run(facts);
    expect(results.events.length).toBe(0); // ❌ Should fail
  });
});
```

---

### **How This Works**
- **Validates buyer firms exist in firms list** → `arrayContainsAny` on firm codes.
- **Ensures firms mapped to buyer firms have the correct market segment** → `arrayContainsAll`.
- **Uses JSONPath filtering** → `$[?(@.firm_code in trade.legs[*].buyer_firm)]`.

---

### **Expected Behavior**
✅ **Passes** when all buyer firms exist **and match the correct market segment**.  
❌ **Fails** if a buyer firm exists **but does not match the market segment**.

This ensures strict validation for trade legs and buyer firms within firms, using your defined operators. 🚀 Let me know if you'd like further refinements!
Here’s everything together:  
1️⃣ **Time Extraction & Conversion Function** (`timeUtils.ts`)  
2️⃣ **Custom Time Comparison Operators** (`timeOperator.ts`)  
3️⃣ **Jest Test Case for Both** (`timeUtils.test.ts`)  

---

### **1️⃣ Time Extraction & Conversion Function (`timeUtils.ts`)**
```typescript
import { format } from "date-fns";
import { utcToZonedTime } from "date-fns-tz";

const timeZoneMap: Record<string, string> = {
    "AU": "Australia/Sydney",
    "NZ": "Pacific/Auckland"
};

/**
 * Extracts only the time (`HH:mm:ss`) and converts AU to NZ timezone if needed.
 * @param {string} timeString - Date-time string in format `yyyy-MM-dd HH:mm:ss`
 * @param {string} targetTZ - Target timezone ("AU" or "NZ")
 * @returns {string} Time in `HH:mm:ss` format
 */
export function extractTimeAndConvert(timeString: string, targetTZ: "AU" | "NZ"): string {
    const auTime = utcToZonedTime(new Date(timeString), timeZoneMap["AU"]);
    const convertedTime = utcToZonedTime(auTime, timeZoneMap[targetTZ]);
    return format(convertedTime, "HH:mm:ss");
}
```
✅ **Extracts only time (`HH:mm:ss`) from timestamp.**  
✅ **Handles timezone conversion (`AU → NZ`).**  
✅ **Uses `date-fns-tz` for accurate time adjustments.**  

---

### **2️⃣ Custom Time Comparison Operators (`timeOperator.ts`)**
```typescript
import { Engine } from "json-rules-engine";

// Initialize JSON rules engine
const engine = new Engine();

// Helper function to convert time `hh:mm:ss` to total seconds
const timeToSeconds = (time: string): number => {
    const [hh, mm, ss] = time.split(":").map(Number);
    return hh * 3600 + mm * 60 + ss;
};

// Define multiple comparison operators
engine.addOperator("timeGreaterThan", (factValue: string, ruleValue: string) => {
    return timeToSeconds(factValue) > timeToSeconds(ruleValue);
});

engine.addOperator("timeGreaterThanOrEqual", (factValue: string, ruleValue: string) => {
    return timeToSeconds(factValue) >= timeToSeconds(ruleValue);
});

engine.addOperator("timeLessThan", (factValue: string, ruleValue: string) => {
    return timeToSeconds(factValue) < timeToSeconds(ruleValue);
});

engine.addOperator("timeLessThanOrEqual", (factValue: string, ruleValue: string) => {
    return timeToSeconds(factValue) <= timeToSeconds(ruleValue);
});

engine.addOperator("timeEqual", (factValue: string, ruleValue: string) => {
    return timeToSeconds(factValue) === timeToSeconds(ruleValue);
});

export default engine;
```
✅ **Supports comparisons for `>` `>=` `<` `<=` `===` using time in `hh:mm:ss` format.**  
✅ **Converts time to total seconds for precise validation.**  

---

### **3️⃣ Jest Test Case (`timeUtils.test.ts`)**
```typescript
import { extractTimeAndConvert } from "./timeUtils";
import engine from "./timeOperator";

describe("Time Extraction and Conversion", () => {
    test("Extract time from AU timestamp", () => {
        expect(extractTimeAndConvert("2020-10-30 01:40:14", "AU")).toBe("01:40:14");
    });

    test("Convert AU time to NZ timezone", () => {
        expect(extractTimeAndConvert("2020-10-30 01:40:14", "NZ")).toBe("03:40:14"); // Adjusted for timezone shift
    });
});

describe("Time Comparison Operator", () => {
    test("Trade time is valid (>= 08:30:00)", async () => {
        const facts = { trade_time: "09:15:30" };
        const rule = {
            conditions: {
                all: [
                    { fact: "trade_time", operator: "timeGreaterThanOrEqual", value: "08:30:00" }
                ]
            },
            event: { type: "timeValidationSuccess" }
        };

        engine.addRule(rule);
        const results = await engine.run(facts);

        expect(results.events.length).toBe(1);
        expect(results.events[0].type).toBe("timeValidationSuccess");
    });

    test("Trade time is invalid (< 08:30:00)", async () => {
        const facts = { trade_time: "07:15:30" };
        const rule = {
            conditions: {
                all: [
                    { fact: "trade_time", operator: "timeGreaterThanOrEqual", value: "08:30:00" }
                ]
            },
            event: { type: "timeValidationFailure" }
        };

        engine.addRule(rule);
        const results = await engine.run(facts);

        expect(results.events.length).toBe(0); // Should not trigger validation
    });

    test("Trade time must match exactly `10:00:00`", async () => {
        const facts = { trade_time: "10:00:00" };
        const rule = {
            conditions: {
                all: [
                    { fact: "trade_time", operator: "timeEqual", value: "10:00:00" }
                ]
            },
            event: { type: "exactTimeMatchSuccess" }
        };

        engine.addRule(rule);
        const results = await engine.run(facts);

        expect(results.events.length).toBe(1);
        expect(results.events[0].type).toBe("exactTimeMatchSuccess");
    });
});
```

✅ **Tests both time extraction and conversion (`AU → NZ`).**  
✅ **Validates the custom operators for time comparison.**  
✅ **Ensures trade time matches expected thresholds and exact values.**  

---

### **Would You Like Additional Edge Case Handling?**
🚀 Let me know if you need refinements for **invalid time formats, daylight savings, or extended comparisons!**  
Or if you need a **PostgreSQL version**, I can help with that too.



Perfect! Here's just the **`arrayCountEquals` operator** and a **unit test** using plain Node.js and Jest (or any test runner).

---

## ✅ 1. Operator Only

```ts
// array-operators.ts
import { Engine } from 'json-rules-engine';

export const addArrayOperators = (engine: Engine) => {
  engine.addOperator(
    'arrayCountEquals',
    (factValue: any[], jsonValue: { value: number; match: Record<string, any> }) => {
      if (!Array.isArray(factValue) || typeof jsonValue !== 'object') return false;

      const { value: expectedCount, match } = jsonValue;
      if (typeof expectedCount !== 'number' || typeof match !== 'object') return false;

      const actualCount = factValue.filter(item =>
        Object.entries(match).every(([key, val]) => item[key] === val)
      ).length;

      return actualCount === expectedCount;
    }
  );
};
```

---

## ✅ 2. Unit Test (Jest Example)

```ts
// array-operators.test.ts
import { Engine } from 'json-rules-engine';
import { addArrayOperators } from './array-operators';

describe('arrayCountEquals operator', () => {
  let engine: Engine;

  beforeEach(() => {
    engine = new Engine();
    addArrayOperators(engine);
  });

  test('triggers when exactly one match exists', async () => {
    engine.addRule({
      conditions: {
        all: [
          {
            fact: 'users',
            operator: 'arrayCountEquals',
            value: {
              value: 1,
              match: { status: 'inactive' }
            }
          }
        ]
      },
      event: {
        type: 'matched',
        params: { message: 'Exactly one inactive user' }
      }
    });

    const facts = {
      users: [
        { id: 1, status: 'active' },
        { id: 2, status: 'inactive' },
        { id: 3, status: 'active' }
      ]
    };

    const { events } = await engine.run(facts);
    expect(events).toHaveLength(1);
    expect(events[0].params.message).toBe('Exactly one inactive user');
  });

  test('does not trigger when no match exists', async () => {
    engine.addRule({
      conditions: {
        all: [
          {
            fact: 'users',
            operator: 'arrayCountEquals',
            value: {
              value: 1,
              match: { status: 'inactive' }
            }
          }
        ]
      },
      event: {
        type: 'matched',
        params: { message: 'Exactly one inactive user' }
      }
    });

    const facts = {
      users: [
        { id: 1, status: 'active' },
        { id: 2, status: 'active' }
      ]
    };

    const { events } = await engine.run(facts);
    expect(events).toHaveLength(0);
  });

  test('does not trigger when more than one match exists', async () => {
    engine.addRule({
      conditions: {
        all: [
          {
            fact: 'users',
            operator: 'arrayCountEquals',
            value: {
              value: 1,
              match: { status: 'inactive' }
            }
          }
        ]
      },
      event: {
        type: 'matched',
        params: { message: 'Exactly one inactive user' }
      }
    });

    const facts = {
      users: [
        { id: 1, status: 'inactive' },
        { id: 2, status: 'inactive' },
        { id: 3, status: 'active' }
      ]
    };

    const { events } = await engine.run(facts);
    expect(events).toHaveLength(0);
  });
});
```

---

## 🧪 Run Test

If you're using Jest:

```bash
npx jest array-operators.test.ts
```

---

Let me know if you also want to test:

* Nested field matching (e.g., `user.meta.status`)
* Greater than or less than count (e.g., `arrayCountGreaterThan`)





You're right! Instead of just tracking the fields, we should dynamically extract all **remaining parameters** from `engineResult` failure events, ensuring they’re properly included in the validation result.

### **Updated Processing Logic**
```typescript
const processValidationResult = (engineResult: any, tradeId: string): ValidationResult => {
  const successEvents: ValidationDetail[] = [];
  const warningEvents: ValidationDetail[] = [];
  const failureEvents: ValidationDetail[] = [];

  // Process success and warning events
  engineResult.events.forEach((event: any) => {
    const detail = {
      level: event.params.level || 'trade',
      severity: event.params.severity || 'success',
      message: event.params.message,
      fields: event.params.fields || [], // Store list of validated fields
      legId: event.params.legId,
    };

    if (detail.severity === 'success') {
      successEvents.push(detail);
    } else if (detail.severity === 'warning') {
      warningEvents.push(detail);
    }
  });

  // Include all remaining parameters in failure results
  engineResult.failureEvents.forEach((event: any) => {
    failureEvents.push({
      level: event.params.level || 'trade',
      severity: 'error',
      message: event.params.message,
      fields: event.params.fields || [],
      legId: event.params.legId,
      ...event.params, // Dynamically include all additional parameters
    });
  });

  // Effective status calculated **only from success and warning events**
  const status = warningEvents.length > 0 ? 'warning' : 'success';

  return { tradeId, status, successEvents, warningEvents, failureEvents };
};
```

### **Example Output**
```json
{
  "tradeId": "T12345",
  "status": "warning",
  "successEvents": [
    {
      "level": "trade",
      "severity": "success",
      "message": "Trade type is valid",
      "fields": ["tradeType"]
    }
  ],
  "warningEvents": [
    {
      "level": "leg",
      "severity": "warning",
      "message": "Leg price is unusually high",
      "fields": ["price"],
      "legId": "L67890"
    }
  ],
  "failureEvents": [
    {
      "level": "trade",
      "severity": "error",
      "message": "Trade date is missing",
      "fields": ["tradeDate"],
      "tradeDate": "2024-10-18", // Dynamically included from event params
      "additionalParam1": "XYZ",
      "additionalParam2": 1234
    }
  ]
}
```

### **Key Enhancements**
✅ **Automatically includes all remaining parameters from failure events**  
✅ **Ensures a structured output while keeping reference failures separate**  
✅ **Maintains clean separation between success, warnings, and failures**  

Would you like to refine this further with **error codes or standardized messages**? 🚀


function validateTradeLegPrices(trades: Trade[], instruments: Instrument[]): Trade[] {
  const instrumentMap = new Map<string, number>();
  
  // Build a quick lookup map for instrument increments
  instruments.forEach(inst => {
    instrumentMap.set(inst.instrumentId, inst.increment);
  });

  return trades.map(trade => {
    const updatedLegs = trade.legs.map(leg => {
      const increment = instrumentMap.get(leg.instrumentId);
      const isDivisible = increment ? (leg.price % increment === 0) : false;
      return {
        ...leg,
        isPriceDivisible: isDivisible
      };
    });

    return {
      ...trade,
      legs: updatedLegs
    };
  });
}




import { EventBridgeEvent, APIGatewayProxyEvent } from "aws-lambda";

export function validateAndExtractPayload(event: EventBridgeEvent<"TradeEvent", any> | APIGatewayProxyEvent): any {
    if ("source" in event) {
        // EventBridge event → validate source
        if (!event.source || event.source !== "aws.events") {
            throw new Error(`Invalid event source: ${event.source}`);
        }

        if (!event.detail) {
            throw new Error("EventBridge event is missing 'detail'");
        }

        return event.detail;
    } else if ("body" in event) {
        // API Gateway event → parse body safely
        if (!event.body) {
            throw new Error("API Gateway event is missing 'body'");
        }

        try {
            return JSON.parse(event.body);
        } catch (error) {
            throw new Error("Invalid JSON format in API Gateway body");
        }
    } else {
        throw new Error("Unknown event format: missing 'source' or 'body'");
    }
}

import express from "express";
import { handler } from "../handlers/lambdaHandler";

const app = express();
app.use(express.json());

app.post("/business-rules/event", async (req, res) => {
    try {
        const result = await handler(req.body as any, {} as any);
        res.status(200).json(result);
    } catch (error) {
        res.status(500).json({ error: "Failed to process event", details: error.message });
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Express server running on port ${PORT}`));


Here’s your **updated annual self-review** with the additional point about participating in the architecture forum:

---

### 🛠️ **Technical Contributions & Delivery**

* Among the first developers on the project; established the local development environment by identifying software access issues and documenting solutions in Confluence — now the go-to guide for new developers.
* Analyzed requirements and led initial estimations for the FIX Gateway component, later refining them with a simpler and more efficient solution.
* Worked closely with the CHESS team to quickly ramp up on FIX Gateway concepts, enabling a deep understanding and faster delivery within a short timeframe.
* Designed and developed a fit-for-purpose FIX Gateway, driving refactoring and integration efforts while collaborating with partner teams and stakeholders.
* Proactively developed a reusable Kafka wrapper library, which is now used across multiple Kafka-based implementations.
* Successfully owned and delivered the complex Business Rule Engine component, ensuring extensibility, performance, and robustness.
* Completed the rule engine library and integrated it with prefiltering and execution Lambdas, enabling timely availability for testing.

---

### 💡 **Design & Architecture**

* Actively contributed to both high-level and low-level design discussions, providing actionable feedback and driving continuous improvements.
* Participated in the organization’s Architecture Forum, regularly contributing to discussions and sharing practical insights from implementation experience.
* Provided architectural input to ensure maintainability, scalability, and reusability across core components.

---

### ⚙️ **DevOps & Infrastructure**

* Worked actively with DevOps to provide input on infrastructure setup, deployment automation, and platform best practices.
* Ensured alignment between development tooling and long-term maintainability and operational excellence.

---

### 🤝 **Collaboration & Mentorship**

* Consistently performed thorough code reviews and provided constructive feedback to ensure high-quality deliverables.
* Mentored new joiners and junior developers, supporting fast onboarding and knowledge transfer.
* Played an active role in fostering a strong and collaborative team culture.

---

### 🌐 **Cross-Team Engagement**

* Actively supported NRTC developers beyond the TradeAccept domain by sharing FIX Gateway knowledge and FIX pattern best practices for wider adoption.

---

Let me know if you want a summary version, a narrative paragraph form, or a self-rating section to pair with it.

Thanks — it sounds like you're using the **`lambda.invoke()`** method (likely from AWS SDK v2) in **TypeScript**, and you're getting a **binary (Buffer)** response.

Let’s walk through **how to properly invoke the Lambda function and decode the response**.

---

## ✅ 1. Setup for AWS SDK v2

If you're using **AWS SDK v2**, install it if needed:

```bash
npm install aws-sdk
```

### 🔧 TypeScript Example using `lambda.invoke()`

```ts
import AWS from 'aws-sdk';

// Initialize Lambda client
const lambda = new AWS.Lambda({ region: 'your-region' });

async function invokeLambda() {
  const params = {
    FunctionName: 'your-lambda-function-name',
    Payload: JSON.stringify({ key: 'value' }), // Input to your Lambda
  };

  try {
    const response = await lambda.invoke(params).promise();

    if (response.Payload) {
      const decodedPayload = Buffer.isBuffer(response.Payload)
        ? response.Payload.toString('utf-8')
        : response.Payload;

      try {
        const parsed = JSON.parse(decodedPayload as string);
        console.log('Lambda response:', parsed);

        // Optional: if Lambda returns a proxy response (like via API Gateway)
        if (parsed.isBase64Encoded) {
          const decodedBody = Buffer.from(parsed.body, 'base64').toString('utf-8');
          console.log('Decoded body:', JSON.parse(decodedBody));
        }

      } catch (parseErr) {
        console.log('Could not parse JSON:', decodedPayload);
      }
    } else {
      console.log('No response payload.');
    }

  } catch (error) {
    console.error('Invoke error:', error);
  }
}

invokeLambda();
```

---

## ⚠️ Binary Response? Here's Why

* Lambda returns a `Buffer` by default in `response.Payload` — **even if it’s just JSON.**
* If your Lambda returns `application/octet-stream` or sets `isBase64Encoded: true`, the body needs to be decoded.

---

## ✅ 2. Fix your Lambda Response (Optional)

If you want to **avoid Base64/binary issues**, structure your Lambda like this (Node.js):

```ts
exports.handler = async (event) => {
  return {
    statusCode: 200,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message: "Hello from Lambda" }),
    isBase64Encoded: false,
  };
};
```

---

Would you like to share:

* The exact `response.Payload` you're getting?
* Whether you're invoking a Lambda via **API Gateway proxy** or just directly?

That will help me narrow down the fix more precisely.


If you're using `lambda.invoke()` in TypeScript (Node.js app) and want to **invoke another Lambda and return its response cleanly** (decoded, JSON parsed, and usable), here's exactly how to do it.

---

## ✅ Final Version: Invoke Lambda and Return Response (TypeScript)

```ts
import AWS from 'aws-sdk';

// Create Lambda client
const lambda = new AWS.Lambda({ region: 'your-region' });

export async function callAnotherLambdaAndReturn() {
  const params = {
    FunctionName: 'your-lambda-function-name',
    InvocationType: 'RequestResponse', // Synchronous invocation
    Payload: JSON.stringify({ key: 'value' }), // Input to the other Lambda
  };

  try {
    const response = await lambda.invoke(params).promise();

    if (!response.Payload) {
      throw new Error('Empty response from Lambda');
    }

    // Convert Buffer (or string) to string
    const rawPayload = Buffer.isBuffer(response.Payload)
      ? response.Payload.toString('utf-8')
      : response.Payload;

    // Parse JSON
    const parsed = JSON.parse(rawPayload as string);

    // Handle case where Lambda returns an API Gateway-style response
    if (parsed.isBase64Encoded && parsed.body) {
      const decodedBody = Buffer.from(parsed.body, 'base64').toString('utf-8');
      return JSON.parse(decodedBody);
    }

    // Otherwise just return the parsed result
    return parsed;

  } catch (err) {
    console.error('Lambda invoke failed:', err);
    throw err;
  }
}
```

---

## ✅ Example Use:

```ts
(async () => {
  const result = await callAnotherLambdaAndReturn();
  console.log('Lambda result:', result);
})();
```

---

## 🧠 Quick Tips

* If you're invoking a Lambda that uses **API Gateway proxy integration**, its response might be:

  ```json
  {
    "statusCode": 200,
    "headers": {},
    "body": "base64-encoded string",
    "isBase64Encoded": true
  }
  ```

  Hence the check for `isBase64Encoded`.

* If it's a direct Lambda, just return plain objects and avoid Buffers/Streams.

---

Would you like help formatting the Lambda you're invoking, so it always returns UTF-8 JSON?


import { S3Client, ListBucketsCommand } from "@aws-sdk/client-s3";

const REGION = process.env.AWS_REGION || "ap-southeast-2";

const s3Client = new S3Client({ region: REGION });

async function listBuckets(): Promise<void> {
  try {
    const data = await s3Client.send(new ListBucketsCommand({}));
    console.log("Buckets:", data.Buckets);
  } catch (err) {
    console.error("Error", err);
  }
}

listBuckets();

Perfect — the script I shared **already includes both the schema and the data**, because of this command:

```bash
pg_dump -Fc "$REMOTE_DB" -f "$DB_DUMP"
```

By default, `pg_dump` includes **everything**: schema, tables, indexes, functions, data, and more — **unless you filter it**.

However, to make it 100% clear and complete for your case, here's the **final version** of the shell script with:

* ✅ Role dump
* ✅ Full schema and data
* ✅ Variable-based config
* ✅ Clean restore into a local DB
* ✅ Better safety (password prompts optional)

---

## ✅ FINAL SCRIPT — `pg_full_clone.sh`

```bash
#!/bin/bash

# === 🔧 CONFIGURATION ===
REMOTE_HOST="your.remote.host"
REMOTE_PORT="5432"
REMOTE_USER="remote_user"
REMOTE_DB="remote_db"
REMOTE_PASSWORD="remote_password"

LOCAL_DB="local_db"
LOCAL_USER="local_user"
LOCAL_PASSWORD="local_password"

DUMP_DIR="./pg_dump_temp"
ROLES_DUMP="$DUMP_DIR/roles.sql"
DB_DUMP="$DUMP_DIR/db.dump"

# === 🚀 START ===
echo "[*] Starting PostgreSQL full database clone..."

# Ensure dump directory exists
mkdir -p "$DUMP_DIR"

# Step 0: Clean up previous files if needed
rm -f "$ROLES_DUMP" "$DB_DUMP"

# === 🔑 Set remote password
export PGPASSWORD="$REMOTE_PASSWORD"

# === 🧾 Step 1: Dump Roles (Users)
echo "[*] Dumping roles (users) from remote..."
pg_dumpall -h "$REMOTE_HOST" -p "$REMOTE_PORT" -U "$REMOTE_USER" --roles-only > "$ROLES_DUMP"
if [ $? -ne 0 ]; then
  echo "[✗] Failed to dump roles. Exiting."
  exit 1
fi

# === 📦 Step 2: Dump Remote Database (Schema + Data)
echo "[*] Dumping full remote database (schema + data)..."
pg_dump -h "$REMOTE_HOST" -p "$REMOTE_PORT" -U "$REMOTE_USER" -d "$REMOTE_DB" -Fc -f "$DB_DUMP"
if [ $? -ne 0 ]; then
  echo "[✗] Failed to dump database. Exiting."
  exit 1
fi

# === 🔑 Set local password
export PGPASSWORD="$LOCAL_PASSWORD"

# === 🔁 Step 3: Create Roles Locally
echo "[*] Recreating roles on local..."
psql -U "$LOCAL_USER" -f "$ROLES_DUMP"
if [ $? -ne 0 ]; then
  echo "[✗] Failed to create roles locally. Exiting."
  exit 1
fi

# === 🆕 Step 4: Create Fresh Local DB
echo "[*] Creating fresh local database: $LOCAL_DB"
dropdb -U "$LOCAL_USER" "$LOCAL_DB" 2>/dev/null
createdb -U "$LOCAL_USER" "$LOCAL_DB"
if [ $? -ne 0 ]; then
  echo "[✗] Failed to create local database. Exiting."
  exit 1
fi

# === ♻️ Step 5: Restore Full Dump (Schema + Data)
echo "[*] Restoring into local database..."
pg_restore -U "$LOCAL_USER" -d "$LOCAL_DB" -Fc --clean --verbose "$DB_DUMP"
if [ $? -ne 0 ]; then
  echo "[✗] Restore failed. Check log."
  exit 1
fi

# === ✅ DONE ===
echo "[✓] Successfully cloned '$REMOTE_DB' (with schema + data + roles) to local DB '$LOCAL_DB'."

# Optional: Cleanup
# rm -rf "$DUMP_DIR"

# Unset passwords
unset PGPASSWORD
```

---

## ✅ How to Use

1. Save the script as `pg_full_clone.sh`
2. Make it executable:

```bash
chmod +x pg_full_clone.sh
```

3. Edit the `REMOTE_` and `LOCAL_` variables to match your setup.
4. Run it:

```bash
./pg_full_clone.sh
```

---

Let me know if you want:

* a version that prompts for values
* `.pgpass` support
* or Docker support!





Here's a complete example of using **AWS Powertools Logger** in TypeScript, ensuring logs are written to a file **only in the local environment** while maintaining structured logging:

### **Step 1: Install Dependencies**
If you haven't installed AWS Powertools for TypeScript, run:
```sh
npm install @aws-lambda-powertools/logger dotenv
```

### **Step 2: Create a `logger.ts` File**
This file sets up the logger and ensures logs are written to a file **only when running locally**.

```typescript
import { Logger } from '@aws-lambda-powertools/logger';
import fs from 'fs';
import dotenv from 'dotenv';

dotenv.config(); // Load environment variables from .env file

const logger = new Logger({ serviceName: 'MyService' });
const logFile = 'logs.txt';

// Function to write logs to a file in local environment
const writeLogToFile = (level: string, message: string, meta?: any) => {
  if (process.env.NODE_ENV === 'local') { // Only log to file in local environment
    const logEntry = JSON.stringify({ level, message, meta, timestamp: new Date().toISOString() });
    fs.appendFileSync(logFile, logEntry + '\n');
  }
};

// Override logger methods to also write logs to a file
const originalInfo = logger.info.bind(logger);
logger.info = (message: string, meta?: any) => {
  writeLogToFile('INFO', message, meta);
  originalInfo(message, meta);
};

const originalDebug = logger.debug.bind(logger);
logger.debug = (message: string, meta?: any) => {
  writeLogToFile('DEBUG', message, meta);
  originalDebug(message, meta);
};

const originalError = logger.error.bind(logger);
logger.error = (message: string, meta?: any) => {
  writeLogToFile('ERROR', message, meta);
  originalError(message, meta);
};

export default logger;
```

### **Step 3: Create a `.env` File**
Define the environment variable for local execution:
```
NODE_ENV=local
```

### **Step 4: Use the Logger in Your Lambda Function**
```typescript
import logger from './logger';

export const handler = async (event: any) => {
  logger.info('Lambda function invoked', { event });

  try {
    logger.debug('Processing event', { event });

    return {
      statusCode: 200,
      body: JSON.stringify({ message: 'Success' }),
    };
  } catch (error) {
    logger.error('Error occurred', { error });
    throw error;
  }
};
```

### **How It Works**
- Logs will be written to `logs.txt` **only when running locally** (`NODE_ENV=local`).
- In AWS Lambda, logs will be sent to **CloudWatch** as usual.
- The logger is reusable across different parts of your application.

Would this setup work for you? 🚀 Let me know if you need any modifications!



Certainly! Here’s the **updated function** with **test cases** covering various scenarios.

---

### **Updated Function**
```typescript
function assignSequenceNumbersByGroup(instruments: Instrument[]): SequencedInstrument[] {
  const groupedInstruments = new Map<string, Instrument[]>();

  // Group by trade_sub_type, commodity_code, and instrument_type
  for (const instrument of instruments) {
    const groupKey = `${instrument.trade_sub_type}-${instrument.commodity_code}-${instrument.instrument_type}`;
    if (!groupedInstruments.has(groupKey)) {
      groupedInstruments.set(groupKey, []);
    }
    groupedInstruments.get(groupKey)!.push(instrument);
  }

  const result: SequencedInstrument[] = [];

  // Process each group separately
  for (const [groupKey, group] of groupedInstruments) {
    group.sort((a, b) => a.expiry_date.localeCompare(b.expiry_date)); // Sort by expiry date

    let sequenceNumber = 0;
    const expiryDateMap = new Map<string, number>();

    for (const instrument of group) {
      if (!expiryDateMap.has(instrument.expiry_date)) {
        sequenceNumber++;
        expiryDateMap.set(instrument.expiry_date, sequenceNumber);
      }
      result.push({
        ...instrument,
        sequence_number: expiryDateMap.get(instrument.expiry_date)!,
      });
    }
  }

  return result;
}
```

---

### **Test Cases**
```typescript
function runTests() {
  const testCases: { description: string; input: Instrument[]; expected: SequencedInstrument[] }[] = [
    {
      description: "Basic case with distinct expiry dates in each group",
      input: [
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01" },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-02" },
        { trade_sub_type: "B", commodity_code: "Y", instrument_type: "T2", expiry_date: "2025-07-01" },
        { trade_sub_type: "B", commodity_code: "Y", instrument_type: "T2", expiry_date: "2025-07-02" }
      ],
      expected: [
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01", sequence_number: 1 },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-02", sequence_number: 2 },
        { trade_sub_type: "B", commodity_code: "Y", instrument_type: "T2", expiry_date: "2025-07-01", sequence_number: 1 },
        { trade_sub_type: "B", commodity_code: "Y", instrument_type: "T2", expiry_date: "2025-07-02", sequence_number: 2 }
      ]
    },
    {
      description: "Duplicate expiry dates within a group should reuse sequence number",
      input: [
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01" },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-02" },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01" } // Duplicate expiry
      ],
      expected: [
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01", sequence_number: 1 },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-02", sequence_number: 2 },
        { trade_sub_type: "A", commodity_code: "X", instrument_type: "T1", expiry_date: "2025-06-01", sequence_number: 1 } // Reused
      ]
    },
    {
      description: "Multiple distinct groups each resetting sequence number",
      input: [
        { trade_sub_type: "C", commodity_code: "Z", instrument_type: "T3", expiry_date: "2025-08-01" },
        { trade_sub_type: "C", commodity_code: "Z", instrument_type: "T3", expiry_date: "2025-08-02" },
        { trade_sub_type: "D", commodity_code: "W", instrument_type: "T4", expiry_date: "2025-09-01" },
        { trade_sub_type: "D", commodity_code: "W", instrument_type: "T4", expiry_date: "2025-09-02" }
      ],
      expected: [
        { trade_sub_type: "C", commodity_code: "Z", instrument_type: "T3", expiry_date: "2025-08-01", sequence_number: 1 },
        { trade_sub_type: "C", commodity_code: "Z", instrument_type: "T3", expiry_date: "2025-08-02", sequence_number: 2 },
        { trade_sub_type: "D", commodity_code: "W", instrument_type: "T4", expiry_date: "2025-09-01", sequence_number: 1 },
        { trade_sub_type: "D", commodity_code: "W", instrument_type: "T4", expiry_date: "2025-09-02", sequence_number: 2 }
      ]
    }
  ];

  for (const testCase of testCases) {
    const actual = assignSequenceNumbersByGroup(testCase.input);
    console.log(`Test Case: ${testCase.description}`);
    console.log("Expected:", JSON.stringify(testCase.expected, null, 2));
    console.log("Actual:", JSON.stringify(actual, null, 2));
    console.log(actual.every((item, index) => JSON.stringify(item) === JSON.stringify(testCase.expected[index])) 
      ? "✅ Passed" 
      : "❌ Failed");
    console.log("---");
  }
}

// Run test cases
runTests();
```

---

### **Key Features of Tests**
✅ **Handles groups correctly**—each group starts sequencing from `1`  
✅ **Checks duplicate expiry dates reuse sequence numbers**  
✅ **Ensures groups with distinct trade subtypes reset sequence**  
✅ **Logs expected vs actual results**  

This test suite should give a **clear pass/fail output** for each case. 🚀 Let me know if you'd like any refinements!


#!/bin/bash

export HOST="your-db.xxxxxx.rds.amazonaws.com"
export REGION="ap-southeast-2"
export USER="your-db-user"
export DBNAME="your-db-name"

# Generate token
export PGPASSWORD=$(aws rds generate-db-auth-token \
  --hostname $HOST \
  --port 5432 \
  --region $REGION \
  --username $USER)

# Connect
psql \
  --host=$HOST \
  --port=5432 \
  --username=$USER \
  --dbname=$DBNAME \
  --sslmode=require


You can **post-process the engine result** using the facts provided to dynamically replace placeholders (`{{factName}}`) in event messages. Here's a structured approach:

### **Step 1: Process Engine Events Using Facts**
Modify the triggered events by injecting **fact values**.

```typescript
async function processEngineResult(engineResult, facts) {
  return await Promise.all(
    engineResult.events.map(event => {
      if (!event.params?.message) return event;

      return {
        ...event,
        params: {
          ...event.params,
          message: renderTemplate(event.params.message, facts) // Replace placeholders dynamically
        }
      };
    })
  );
}
```

---

### **Step 2: Replace Placeholders in Event Messages**
Ensure fact-based replacement for **nested paths** like `"leg.symbol"`.

```typescript
function renderTemplate(template: string, data: Record<string, any>): string {
  return template.replace(/\{\{([^}]+)\}\}/g, (match, path) => {
    try {
      // Support nested paths like 'user.profile.name'
      const value = path.split('.').reduce((obj, key) => obj?.[key], data);
      return value !== undefined ? value : match;
    } catch {
      return match;
    }
  });
}
```

---

### **Step 3: Integrate with Rule Engine Execution**
Pass facts and process results dynamically.

```typescript
async function runRuleEngine(engine, facts) {
  const engineResult = await engine.run();
  const processedEvents = await processEngineResult(engineResult, facts);

  processedEvents.forEach(event => console.log(event.params.message));
}

// Example facts
const facts = {
  instruments: [
    { instrument_code: "AAPL" },
    { instrument_code: "GOOGL" }
  ],
  leg: { symbol: "AAPL" }
};

// Execute rule engine
const engine = new Engine(rules);
runRuleEngine(engine, facts);
```

---

### **How It Works**
✅ **Retrieves all triggered events** from `json-rules-engine`.  
✅ **Uses facts to inject real-time values** in event messages.  
✅ **Supports deeply nested placeholders** (`{{leg.symbol}}`).  

Would you like enhancements like **structured logging**, **handling missing values**, or **error reporting**? 🚀  
✅ Your **Chain of Responsibility rule engine** starter project is now scaffolded as a modular TypeScript library. It includes:

* ✅ `RuleOutcomeType`, `RuleAction`, `RuleResult`, `RuleContext`
* ✅ Base abstract class for rule chaining
* ✅ Two example rules (`FailsIfInactiveRule`, `WarnIfNotAdminRule`)
* ✅ A runner file demonstrating execution and trace output

You can extend this with:

* DI framework integration (like InversifyJS or NestJS)
* Dynamic chain construction (e.g., from config)
* Rule validation or input schemas

Would you like me to add:

* 🔌 InversifyJS DI support?
* 📦 `package.json` and build scripts for publishing?
* 🧪 Unit test examples with Jest or Vitest?




Perfect — let's make your **Chain of Responsibility for rule evaluation** support:

1. ✅ **Success**
2. ⚠️ **Warning**
3. ❌ **Error (failure)**

And still allow:

* Continuing chain,
* Skipping next,
* Stopping chain immediately,
* And capturing detailed **rule trace**.

---

## ✅ Finalized Design

### 1. `RuleOutcomeType` – Semantic outcome of the rule

```ts
type RuleOutcomeType = "SUCCESS" | "WARNING" | "ERROR";
```

### 2. `RuleAction` – Control flow behavior

```ts
type RuleAction = "CONTINUE" | "SKIP" | "STOP";
```

---

### 3. `RuleResult` – Result returned by each rule

```ts
interface RuleResult {
  action: RuleAction;
  outcome: RuleOutcomeType;
  message?: string;
  metadata?: Record<string, any>;
}
```

---

### 4. `RuleContext` – Passed to all rules and records status

```ts
interface RuleTraceEntry {
  ruleName: string;
  outcome: RuleOutcomeType;
  action: RuleAction;
  message?: string;
  metadata?: Record<string, any>;
  timestamp: string;
}

interface RuleContext {
  input: any;
  result?: any;
  ruleTrace: RuleTraceEntry[];
  [key: string]: any;
}
```

---

## 🧱 Base Rule Class

```ts
abstract class BaseRule {
  private nextHandler: BaseRule | null = null;

  public setNext(handler: BaseRule): BaseRule {
    this.nextHandler = handler;
    return handler;
  }

  protected getNext(): BaseRule | null {
    return this.nextHandler;
  }

  public async execute(context: RuleContext): Promise<void> {
    const result = await this.handle(context);

    context.ruleTrace.push({
      ruleName: this.constructor.name,
      outcome: result.outcome,
      action: result.action,
      message: result.message,
      metadata: result.metadata,
      timestamp: new Date().toISOString(),
    });

    switch (result.action) {
      case "STOP":
        return;
      case "SKIP":
        return this.getNext()?.getNext()?.execute(context);
      case "CONTINUE":
        return this.getNext()?.execute(context);
    }
  }

  abstract handle(context: RuleContext): Promise<RuleResult>;
}
```

---

## ✏️ Example Rules

### 1. Error Rule

```ts
class FailsIfInactiveRule extends BaseRule {
  async handle(context: RuleContext): Promise<RuleResult> {
    const user = context.input.user;
    if (!user?.isActive) {
      return {
        action: "STOP",
        outcome: "ERROR",
        message: "User is inactive.",
      };
    }

    return {
      action: "CONTINUE",
      outcome: "SUCCESS",
      message: "User is active.",
    };
  }
}
```

### 2. Warning Rule

```ts
class WarnIfNotAdminRule extends BaseRule {
  async handle(context: RuleContext): Promise<RuleResult> {
    const user = context.input.user;
    if (!user?.isAdmin) {
      return {
        action: "CONTINUE",
        outcome: "WARNING",
        message: "User is not admin. Proceeding with warning.",
      };
    }

    return {
      action: "CONTINUE",
      outcome: "SUCCESS",
      message: "User is admin.",
    };
  }
}
```

---

## 🚀 Chain Execution

```ts
const context: RuleContext = {
  input: { user: { isActive: true, isAdmin: false } },
  ruleTrace: [],
};

const rule1 = new FailsIfInactiveRule();
const rule2 = new WarnIfNotAdminRule();
const rule3 = new SetProcessingFlagRule();

rule1.setNext(rule2).setNext(rule3);

await rule1.execute(context);

console.log("Trace of execution:", context.ruleTrace);
```

---

## 🧠 Sample Output

```json
[
  {
    "ruleName": "FailsIfInactiveRule",
    "outcome": "SUCCESS",
    "action": "CONTINUE",
    "message": "User is active.",
    "timestamp": "2025-06-15T07:50:00.000Z"
  },
  {
    "ruleName": "WarnIfNotAdminRule",
    "outcome": "WARNING",
    "action": "CONTINUE",
    "message": "User is not admin. Proceeding with warning.",
    "timestamp": "2025-06-15T07:50:01.000Z"
  },
  ...
]
```

---

## 🧰 Optional Enhancements

* Support for **rule severity levels** (`info`, `warn`, `error`).
* Dynamic **rule chain building** from config or JSON.
* Generate a **summary** report:

  ```ts
  const errors = context.ruleTrace.filter(r => r.outcome === 'ERROR');
  const warnings = context.ruleTrace.filter(r => r.outcome === 'WARNING');
  ```

---

Would you like this exported as a reusable class library or starter template with DI support?



function resolveStatus(ruleContext: RuleContext): { status: 'success' | 'warning' | 'error'; statusCode: number } {
  let hasError = false;
  let hasStopError = false;
  let hasWarning = false;

  for (const entry of ruleContext.ruleTrace) {
    if (entry.outcome === 'error') {
      hasError = true;
      if (entry.action === 'stop') {
        hasStopError = true;
      }
    } else if (entry.outcome === 'warning') {
      hasWarning = true;
    }
  }

  if (hasStopError) {
    return { status: 'error', statusCode: 400 };
  } else if (hasError) {
    return { status: 'error', statusCode: 202 };
  } else if (hasWarning) {
    return { status: 'warning', statusCode: 202 };
  } else {
    return { status: 'success', statusCode: 200 };
  }
}













Got it — you want to pass the **bucket name** (local folder name) and **file name** as separate inputs (just like in S3). Here's the updated version of the function and test cases that reflects this.

---

## ✅ 1. Updated Function — `fetchRulesFromFile.ts`

```ts
// src/utils/fetchRulesFromFile.ts
import { promises as fs } from 'fs';
import * as path from 'path';
import logger from './logger'; // Use your actual logger

/**
 * Fetches JSON rules from a local file, treating `bucket` as folder and `key` as filename.
 */
export async function fetchRulesFromFile<T>(bucket: string, key: string): Promise<T> {
  const filePath = path.resolve(bucket, key);
  logger.debug('Fetching JSON rules from file', { bucket, key, filePath });

  try {
    const content = await fs.readFile(filePath, 'utf-8');
    const rules = JSON.parse(content) as T;
    logger.debug('Parsed rules from file', { rules });
    return rules;
  } catch (err) {
    logger.error('Failed to read or parse JSON rules file', { filePath, error: err });
    throw err;
  }
}
```

---

## ✅ 2. Sample Rule File (unchanged)

File: `__tests__/fixtures/sample-rules.json`

```json
[
  {
    "conditions": {
      "all": [
        {
          "fact": "age",
          "operator": "greaterThanInclusive",
          "value": 18
        }
      ]
    },
    "event": {
      "type": "adult-check",
      "params": {
        "message": "User is an adult"
      }
    }
  }
]
```

---

## ✅ 3. Updated Test File — `fetchRulesFromFile.test.ts`

```ts
// __tests__/fetchRulesFromFile.test.ts
import path from 'path';
import { fetchRulesFromFile } from '../src/utils/fetchRulesFromFile';

interface Rule {
  conditions: unknown;
  event: {
    type: string;
    params: Record<string, unknown>;
  };
}

describe('fetchRulesFromFile (bucket + key input)', () => {
  const bucket = path.join(__dirname, 'fixtures');
  const validKey = 'sample-rules.json';
  const invalidKey = 'non-existent.json';
  const invalidJsonKey = 'invalid.json';

  it('should load and parse rules from a valid JSON file', async () => {
    const rules = await fetchRulesFromFile<Rule[]>(bucket, validKey);
    expect(Array.isArray(rules)).toBe(true);
    expect(rules[0].event.type).toBe('adult-check');
  });

  it('should throw an error if file does not exist', async () => {
    await expect(fetchRulesFromFile<Rule[]>(bucket, invalidKey)).rejects.toThrow();
  });

  it('should throw a SyntaxError for invalid JSON', async () => {
    await expect(fetchRulesFromFile<Rule[]>(bucket, invalidJsonKey)).rejects.toThrow(SyntaxError);
  });
});
```

---

## ✅ 4. `invalid.json` (for test)

File: `__tests__/fixtures/invalid.json`

```
{ "invalid": true
```

---

Let me know if:

* You want this function to also support loading from both local and S3 using a strategy
* You’d like schema validation (e.g., using `zod` or `ajv`) to validate rule formats
* You want a CLI utility to preview rules from a given path



Certainly! Here's the TypeScript function to validate that the `agreedTradeDate` is **at most one business day before** the `tradeDate`, along with a comprehensive set of test cases:

### ✅ TypeScript Function

```typescript
/**
 * Validates that agreedTradeDate is at most one business day before tradeDate.
 * 
 * @param tradeDateStr - Trade date in "yyyy-MM-dd" format.
 * @param agreedTradeDateStr - Agreed trade date in "yyyy-MM-dd" format.
 * @param previousBusinessDateStr - Previous business day before tradeDate in "yyyy-MM-dd" format.
 * @returns true if agreedTradeDate is the same as or within one calendar day before previous business day.
 */
function validateAgreedTradeDate(
  tradeDateStr: string,
  agreedTradeDateStr: string,
  previousBusinessDateStr: string
): boolean {
  const agreed = new Date(agreedTradeDateStr);
  const previousBiz = new Date(previousBusinessDateStr);

  const msPerDay = 24 * 60 * 60 * 1000;
  const diffDays = (previousBiz.getTime() - agreed.getTime()) / msPerDay;

  return diffDays >= 0 && diffDays <= 1;
}
```

---

### 🧪 Test Cases

```typescript
const tradeDate = "2025-07-14";
const previousBiz = "2025-07-11"; // Calculated previous business day (Friday)

/**
 * Test cases to validate the function
 */
const testCases = [
  { agreed: "2025-07-11", expected: true }, // Same day as previous business day
  { agreed: "2025-07-10", expected: true }, // One day before previous business day
  { agreed: "2025-07-09", expected: false }, // Two days before previous business day
  { agreed: "2025-07-12", expected: false }, // Saturday (not a business day)
  { agreed: "2025-07-13", expected: false }, // Sunday (not a business day)
  { agreed: "2025-07-15", expected: false }, // After previous business day
  { agreed: "2025-07-16", expected: false }, // After previous business day
];

testCases.forEach(({ agreed, expected }) => {
  const result = validateAgreedTradeDate(tradeDate, agreed, previousBiz);
  console.log(`Agreed: ${agreed}, Expected: ${expected}, Result: ${result}`);
});
```

---

### 📋 Expected Output

```
Agreed: 2025-07-11, Expected: true, Result: true
Agreed: 2025-07-10, Expected: true, Result: true
Agreed: 2025-07-09, Expected: false, Result: false
Agreed: 2025-07-12, Expected: false, Result: false
Agreed: 2025-07-13, Expected: false, Result: false
Agreed: 2025-07-15, Expected: false, Result: false
Agreed: 2025-07-16, Expected: false, Result: false
```

---

### 🔍 Explanation

* **Same Day**: If `agreedTradeDate` is the same as `previousBusinessDate`, it's valid.
* **One Day Before**: If `agreedTradeDate` is exactly one calendar day before `previousBusinessDate`, it's valid.
* **Invalid Cases**: If `agreedTradeDate` is more than one day before, on a weekend, or after `previousBusinessDate`, it's invalid.

---

If you need assistance integrating this into your project or handling public holidays, feel free to ask!



To delete records for a specific date before merging new ones, you can add a **pre-merge deletion step** in your `mergeInstruments` method. Here's how to do it cleanly:

---

### 🧼 Step 1: Add a `deleteBeforeMerge` method

This deletes rows from the target table where a specific date column matches the given value:

```ts
async deleteBeforeMerge(
  tableName: string,
  dateColumn: string,
  targetDate: string
): Promise<void> {
  const query = `DELETE FROM ${tableName} WHERE ${dateColumn} = $1::DATE`;
  logger.info('Deleting existing records for date', { query, targetDate });

  try {
    await this.db.none(query, [targetDate]);
  } catch (error) {
    logger.error('Error deleting records before merge', { error });
    throw error;
  }
}
```

---

### 🔁 Step 2: Call it before your merge loop

Update your `mergeInstruments` method like this:

```ts
await this.deleteBeforeMerge(tableName, 'your_date_column', '2024-07-16');
```

Make sure `'your_date_column'` matches the actual column name in your table that stores the date.

---

### 🧠 Why This Works

- Ensures no duplicate or stale data for that date.
- Keeps your merge logic clean and focused.
- Avoids relying on `MERGE` to handle deletions, which can be tricky depending on PostgreSQL version.

---

Want me to help refactor this into a reusable utility or integrate it with a date-driven batch process? I can tailor it to your schema and workflow.
