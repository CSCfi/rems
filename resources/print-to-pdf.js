// Usage: node print-to-pdf.js <user> <api key> <url> <output file>

const puppeteer = require("puppeteer");

(async () => {
    const user = process.argv[2];
    const apiKey = process.argv[3];
    const url = process.argv[4];
    const file = process.argv[5];
    const browser = await puppeteer.launch({args: ['--no-sandbox', '--disable-setuid-sandbox']}); // TODO sandbox!
    const page = await browser.newPage();
    await page.setRequestInterception(true);
    page.on('request', request => {
        const headers = request.headers();
        headers['x-rems-api-key'] = apiKey;
        headers['x-rems-user-id'] = user;
        request.continue({headers});
    });
    page.on('request', request => {
        console.log(request.method(), request.url());
    });
    page.on('console', msg => console.log(msg));
    page.on('pageerror', msg => console.log(msg));
    await page.goto(url, {waitUntil: "networkidle0"});
    await page.pdf({
        path: file,
        format: "A4"
    });
    console.log("generated pdf");
    await browser.close();
})().catch(err => {console.log(err); process.exit(2);});
