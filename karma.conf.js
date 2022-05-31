module.exports = function (config) {
  config.set({
    browsers: ['ChromeHeadless'],
    basePath: 'target/shadow',
    files: ['cljs-test.js'],
    frameworks: ['cljs-test'],
    plugins: ['karma-cljs-test', 'karma-chrome-launcher', 'karma-junit-reporter'],
    reporters: ['progress', 'junit'],
    junitReporter: {outputDir: 'target/test-results'},
    colors: true,
    logLevel: config.LOG_INFO,
    singleRun: true,
    client: {
      args: ["shadow.test.karma.init"],
    }
  })
};
