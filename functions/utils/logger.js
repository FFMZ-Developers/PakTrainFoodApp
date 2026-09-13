function success(message) {

    console.log(
        "✅",
        message
    );

}

function error(message) {

    console.error(
        "❌",
        message
    );

}

function warning(message) {

    console.warn(
        "⚠️",
        message
    );

}

function info(message) {

    console.log(
        "ℹ️",
        message
    );

}

module.exports = {

    success,

    error,

    warning,

    info

};