// FactService.ts
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

// RuleService.ts
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

  async evaluateRules(dynamicFacts: any): Promise<any> {
    for (const [factId, factFn] of Object.entries(dynamicFacts)) {
      this.engine.addFact(factId, factFn);
    }

    const results = await this.engine.run({});
    return results;
  }
}

export default RuleService;

// S3Service.ts
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



import FactService from './FactService';
import RuleService from './RuleService';
import S3Service from './S3Service';

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

  async evaluateRules(rules: any[], dynamicFacts: any): Promise<any> {
    this.ruleService.addRules(rules);
    return this.ruleService.evaluateRules(dynamicFacts);
  }

  async fetchRulesFromS3(bucket: string, key: string): Promise<any> {
    return this.s3Service.fetchRulesFromS3(bucket, key);
  }
}

export default RuleEngineWrapper;





import RuleEngineWrapper from 'rule-engine-wrapper';
import { S3 } from 'aws-sdk';

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



import RuleEngineWrapper from 'rule-engine-wrapper';

// Initialize Rule Engine Wrapper
const ruleEngineWrapper = new RuleEngineWrapper();

// Fact Function for Next Trading Date
const getNextTradingDate = async (params, almanac) => {
  const query = "SELECT trade_date FROM calendar WHERE trade_date_indicator = 'Y' AND trade_date > NOW() ORDER BY trade_date LIMIT 1";
  const data = await ruleEngineWrapper.fetchFactData(query);
  return data[0].trade_date;
};

// Fact Function for Eligible Instruments
const getEligibleInstruments = async (params, almanac) => {
  const nextTradingDate = await almanac.factValue("nextTradingDate");
  const query = `SELECT * FROM instruments WHERE first_trading_date <= '${nextTradingDate}' AND last_trading_date >= '${nextTradingDate}'`;
  const data = await ruleEngineWrapper.fetchFactData(query);
  return data;
};

// Define Dynamic Facts
const dynamicFacts = {
  "nextTradingDate": getNextTradingDate,
  "eligibleInstruments": getEligibleInstruments
};

// Rules with Priority
const rules = [
  {
    "conditions": {
      "all": [
        {
          "fact": "nextTradingDate",
          "operator": "greaterThanInclusive",
          "value": "$today"
        }
      ]
    },
    "event": {
      "type": "nextTradingDate",
      "params": {
        "updateFacts": {
          "nextTradingDate": "$currentDate"
        }
      }
    },
    "priority": 10 // Higher priority to run first
  },
  {
    "conditions": {
      "all": [
        {
          "fact": "eligibleInstruments",
          "operator": "equal",
          "value": true
        }
      ]
    },
    "event": {
      "type": "eligibleInstrument",
      "params": {
        "updateFacts": {
          "eligibleInstruments": "SELECT * FROM instruments WHERE first_trading_date <= $nextTradingDate AND last_trading_date >= $nextTradingDate"
        }
      }
    },
    "priority": 5 // Lower priority to run after the first rule
  }
];





