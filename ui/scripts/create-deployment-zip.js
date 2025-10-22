import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.dirname(__dirname);

console.log('🚀 Creating minimal deployment package for AWS Elastic Beanstalk...');

// Create deployment directory
const deployDir = path.join(projectRoot, 'deploy');
if (fs.existsSync(deployDir)) {
  fs.rmSync(deployDir, { recursive: true, force: true });
}
fs.mkdirSync(deployDir, { recursive: true });

// Files to include in deployment (minimal set)
const filesToCopy = [
  'server.cjs',
  '.env.production'
];

// Directories to copy
const dirsToCopy = [
  'dist'
];

console.log('📁 Copying files to deployment directory...');

// Copy individual files
filesToCopy.forEach(file => {
  const sourcePath = path.join(projectRoot, file);
  const destPath = path.join(deployDir, file);
  
  if (fs.existsSync(sourcePath)) {
    fs.copyFileSync(sourcePath, destPath);
    console.log(`✅ Copied: ${file}`);
  } else {
    console.log(`⚠️  Warning: ${file} not found, skipping...`);
  }
});

// Copy directories
dirsToCopy.forEach(dir => {
  const sourcePath = path.join(projectRoot, dir);
  const destPath = path.join(deployDir, dir);
  
  if (fs.existsSync(sourcePath)) {
    execSync(`cp -r "${sourcePath}" "${destPath}"`, { stdio: 'inherit' });
    console.log(`✅ Copied directory: ${dir}`);
  } else {
    console.log(`⚠️  Warning: ${dir} not found, skipping...`);
  }
});

// Create a minimal package.json for deployment
const packageJson = JSON.parse(fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8'));
const deployPackageJson = {
  name: packageJson.name,
  version: packageJson.version,
  private: packageJson.private,
  type: packageJson.type,
  scripts: {
    start: "node server.cjs"
  },
  // No dependencies needed since we're serving static files
  engines: {
    node: ">=18.0.0"
  }
};

fs.writeFileSync(
  path.join(deployDir, 'package.json'),
  JSON.stringify(deployPackageJson, null, 2)
);

console.log('✅ Created minimal deployment package.json');

// Create zip file
const zipFileName = `deployment-${packageJson.version}-${new Date().toISOString().split('T')[0]}.zip`;
const zipPath = path.join(projectRoot, zipFileName);

console.log('📦 Creating zip file...');

try {
  // Change to deploy directory and create zip
  process.chdir(deployDir);
  execSync(`zip -r "${zipPath}" .`, { stdio: 'inherit' });
  
  console.log(`✅ Deployment package created: ${zipFileName}`);
  console.log(`📁 Location: ${zipPath}`);
  console.log('');
  console.log('🎉 Ready for AWS Elastic Beanstalk deployment!');
  console.log('');
  console.log('📋 Package contents:');
  console.log('  - server.cjs (Node.js server)');
  console.log('  - dist/ (Built static files)');
  console.log('  - package.json (Minimal config)');
  console.log('  - .env.production (Environment variables)');
  console.log('');
  console.log('✨ Benefits:');
  console.log('  - No npm install required on server');
  console.log('  - Faster deployment');
  console.log('  - Smaller package size');
  console.log('  - No dependency conflicts');
  
} catch (error) {
  console.error('❌ Error creating zip file:', error.message);
  process.exit(1);
} 