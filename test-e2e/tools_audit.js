const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    fs.readdirSync(dir).forEach(file => {
        const full = path.join(dir, file);
        if (fs.statSync(full).isDirectory()) results = results.concat(walk(full));
        else if (file.endsWith('.jsx')) results.push(full);
    });
    return results;
}

const files = walk('frontend/src');
const componentsWithForms = [];

files.forEach(f => {
    const c = fs.readFileSync(f, 'utf8');
    const hasForm = c.includes('<form');
    const hasInput = c.includes('<input');
    const hasSelect = c.includes('<select');
    const hasTextarea = c.includes('<textarea');
    const hasPasswordInput = c.includes('PasswordInput');
    const hasModal = c.includes('<Modal');
    
    if (hasForm || hasInput || hasSelect || hasTextarea || hasPasswordInput || hasModal) {
        componentsWithForms.push({
            file: f,
            hasForm,
            hasInput,
            hasSelect,
            hasTextarea,
            hasPasswordInput,
            hasModal
        });
    }
});

console.log('Total interactive components found:', componentsWithForms.length);
componentsWithForms.forEach(item => {
    console.log(item.file, JSON.stringify({
        form: item.hasForm,
        input: item.hasInput,
        select: item.hasSelect,
        textarea: item.hasTextarea,
        passInput: item.hasPasswordInput,
        modal: item.hasModal
    }));
});
