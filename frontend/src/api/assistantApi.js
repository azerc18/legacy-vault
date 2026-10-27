import http from './http.js';

export const assistantApi = {
    chat: (messages) => http.post('/assistant/chat', { messages: messages.slice(-10) })
        .then((r) => r.data.data.reply),
};