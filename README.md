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

