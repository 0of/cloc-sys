const path = require('path'),
      execSync = require('child_process').execSync,
      YAML = require('js-yaml'),
      request = require('request');

var config = loadConfig(process.env["environ"])

function loadConfig(env = "dev") {
  return require(path.join(__dirname, '/config', env))
}

function cloneRepo() {
  const userRepoURL = process.env["USER_REPO"],
        branch = process.env["REPO_BRANCH"];

  execSync(`git clone ${userRepoURL} --branch=${branch} --depth=50 repo`, {cwd: config["user-location"]});
}

function parseAndSubmit(output) {
  const doc = YAML.safeLoad(output),
        jobID = "jobID";
  let langs = [],
      sum = null;

  for (let k in doc) {
    if(doc.hasOwnProperty(k)) {
      let lang = doc[k];

      if (k.toUpperCase() === 'SUM') {
        sum = {total: (lang.blank + lang.code), 
               code: lang.code};
      } else if (k.toUpperCase() === 'HEADER') {
        continue;
      } else {
        // langs
        langs.push({lang: k,
                    total: (lang.blank + lang.code),
                    code: lang.code});
      }
    } 
  }

  // submit
  request.post({uri: `${config["submit-url"]}/submit/${jobID}`,
                json: {user: "user",
                       repo: "repo",
                       branch: "branch",
                       sum: sum,
                       langs: langs}});
}

function runCloc() {
  const userLocalRepoDir = path.join(config["user-location"], 'repo');

  // execute counting line
  let clocOutput = execSync('cloc --yaml --quiet .', {cwd: userLocalRepoDir});
  // parse out
  console.log(clocOutput.toString());

  parseAndSubmit(clocOutput);
}

try {
  cloneRepo();
  runCloc();
} catch(e) {
  request.post({uri: `${config["submit-url"]}/fail/${jobID}`,
                json: {reason: e.toString()}});
}
