module.exports = function (config) {
  config.set({
    browsers: ['ChromeHeadless'],
    files: [
      'node_modules/jquery/dist/jquery.min.js', // XXX: workaround for unit tests expecting js/$
      'target/shadow/cljs-test.js'],
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
