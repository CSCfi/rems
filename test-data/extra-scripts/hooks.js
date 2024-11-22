window.test_navigations = [];
window.rems_hooks = {
    navigate: url => {
        test_navigations.push(url);
    }
};
