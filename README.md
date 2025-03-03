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
import RuleEngineWrapper from '../ruleEngineWrapper';
import FactService from '../services/FactService';
import RuleService from '../services/RuleService';
import S3Service from '../services/S3Service';
import { Rule, Event, Fact } from 'json-rules-engine
