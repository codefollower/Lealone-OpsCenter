const opsResult = { 
    data() {
        return {
            result: ""
        }
    },
    mounted() {
        if(lealone.screen == "ops" && lealone.page == this.gid) {
            this.result = lealone.params.result;
        }
    }
}
