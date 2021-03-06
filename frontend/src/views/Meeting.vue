<template>
	<div class="meeting-container">
		<!-- 미팅 네비바 -->
		<MeetingNavBar :startSignal="startSignal" :closeSignal="closeSignal" @leaveMeeting="leaveSignal=true" />

		<!-- 미팅 메인 -->
		<div class="meeting-content">
			<!-- 미팅 대기실/비디오 -->
			<Video v-if="startSignal" />
			<Waiting v-else @ready="readySignal=!readySignal" @start="startSignal=true" @leave="leaveSignal=true" />

			<!-- 우측 aside -->
			<div 
				v-show="openAside" 
				class="meeting-content-aside" 
				:style="600 < windowWidth ? {'width': '420px'} : {'width': windowWidth - 10 + 'px'}"
			>
				<div class="d-flex flex-row justify-content-between p-2 align-items-center">
					<span v-if="asideCategory.slice(0, 10) === 'evaluation'"><span class="fw-bold ps-2">{{ asideCategory.slice(10) }}</span>님의 면접 평가</span>
					<span v-else class="ps-2">{{ categoryKorName[asideCategory] }}</span>
					<el-button :icon="CloseBold" circle @click="[openAside=!openAside, asideCategory='']" type="text" ></el-button>
				</div>
				
				<Participant v-show="asideCategory === 'participant'" />
				<div v-for="participant in participants" :key="participant">
					<Evaluation 
						:participantNickname="participant.nickname" 
						:endSignal="endSignal" 
						:startSignal="startSignal" 
						v-if="participant.nickname !== this.$store.state.user.nickname"
						v-show="asideCategory === 'evaluation' + participant.nickname" />
				</div>
				<Chat 
					:readySignal="readySignal" 
					:endSignal="endSignal" 
					:startSignal="startSignal" 
					:leaveSignal="leaveSignal"
					@start="startSignal=true" 
					@close="closeSignal=true"
					@leave="leaveSignal=true"
					v-show="asideCategory === 'chat'" 
				/>
				<Memo :endSignal="endSignal" v-show="asideCategory === 'memo'" />
				<File v-if="asideCategory === 'file'" />
				<Timer v-if="asideCategory === 'timer'" />
			</div>
		</div>

		<!-- meeting footer -->
		<MeetingFooter v-model:openAside="openAside" v-model:asideCategory="asideCategory" :startSignal="startSignal" />
	</div>
</template>

<script>
import { defineComponent, ref, onMounted, watch, computed, onUnmounted } from 'vue'
import Participant from '@/components/Meeting/Participant.vue';
import Evaluation from '@/components/Meeting/Evaluation.vue';
import Chat from '@/components/Meeting/Chat.vue';
import Memo from '@/components/Meeting/Memo.vue';
import File from '@/components/Meeting/File.vue';
import Timer from '@/components/Meeting/Timer.vue';
import MeetingNavBar from '@/components/Meeting/MeetingNavBar.vue'
import Video from '@/components/Meeting/Video.vue'
import Waiting from '@/components/Meeting/Waiting.vue'
import MeetingFooter from '@/components/Meeting/MeetingFooter.vue'
import { CloseBold, } from '@element-plus/icons-vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import axios from "axios"

export default defineComponent({
	name: 'Meeting',
	components: {
		MeetingNavBar,
		Participant,
		Evaluation,
		Chat,
		Memo,
		File,
		Video,
		Waiting,
		MeetingFooter,
		Timer
	},
	setup() {
		//카메라, 마이크 접근 권한을 받기 위한 처리
		async function getMedia(){
			try{
				await navigator.mediaDevices.getUserMedia({
					audio: true,
					video: true,
				})
			} catch(e){
				console.log(e);
			}
		}
		getMedia();

		
		const wholeVideosWrapper = ref(null)

		let width = ref(0)
		let height = ref(0)
		let windowWidth = ref(0)

		onMounted(() => {
			document.getElementById('joinButton').onclick=function(){register(); return false;};
			document.getElementById('mute').onclick = function(){handleMuteClick(); return false};
			document.getElementById('camera').onclick = function(){handleCameraClick(); return false};
			document.getElementById('record').onclick=function(){start(); return false;};
			document.getElementById('stopRecording').onclick=function(){stop(); return false;};

			getMeeting()

			// 반응형 비디오 크기 설정
			window.addEventListener('resize', function () {
				let width =  openAside.value ? window.innerWidth - 420 : document.getElementById('container').offsetWidth
				if (window.innerWidth < 600) {
					width = window.innerWidth
				}
				const height = document.getElementById('container').offsetHeight
				resize(width, height)
				windowWidth.value = window.innerWidth
			})
		})
    onUnmounted(() => {
      window.removeEventListener('resize', function () {
				let width =  openAside.value ? window.innerWidth - 420 : document.getElementById('container').offsetWidth
				if (window.innerWidth < 600) {
					width = window.innerWidth
				}
				const height = document.getElementById('container').offsetHeight
				resize(width, height)
				windowWidth.value = window.innerWidth
			})
    })
		const ratio = 9 / 16  // 비디오 화면 비율 (16: 9)
		const setMargin = 10  // 비디오 margin
		let maxWidth = ref(0)  // 비디오 최대 넓이

    const getArea = function(increment, width, height) {
			let i = 0;
			let w = 0;
			let h = increment * ratio + (setMargin * 2);
			while (i < (document.getElementsByClassName('participant')).length) {
					if ((w + increment) > width) {
							w = 0;
							h = h + (increment * ratio) + (setMargin * 2);
					}
					w = w + increment + (setMargin * 2);
					i++;
			}
			if (h > height || increment > width) return false;
			else return increment;
    }

		// 비디오의 너비 계산
		const resize = function (width, height) {
			let max = 0
			let i = 1
			while (i < 5000) {
					let area = getArea(i, width, height);
					if (area === false) {
							max = i - 1;
							break;
					}
					i++;
			}
			max = max - (setMargin * 2)  // remove margins
			maxWidth.value = max
			resizer(max)
		}

		function resizer(width) {
			const participant = document.getElementsByClassName('participant')
			for (var s = 0; s < participant.length; s++) {
					let element = participant[s];
					element.style.margin = setMargin + "px"
					element.style.width = width + "px"
					element.style.height = (width * ratio) + "px"
			}
		}

		const openAside = ref(true)
		const asideCategory = ref('chat')
		const categoryKorName = {
			'chat': '채팅',
			'memo': '메모',
			'participant': '참가자',
			'file': '파일 전송',
			'timer': '타이머'
		}

		watch(openAside, (oldVal) => {
			let width = oldVal ? document.getElementById('container').offsetWidth - 420 : document.getElementById('container').offsetWidth + 420
			let height = document.getElementById('container').offsetHeight
			if (window.innerWidth < 600) {
				width = window.innerWidth
				height = oldVal ? (window.innerHeight - 150) / 2 : window.innerHeight - 150
			}
			resize(width, height)
		})

		const endSignal = ref(false)  
		const startSignal = ref(false)  // 미팅 시작
		const readySignal = ref(false)  // 미팅 준비
		const leaveSignal = ref(false)  // 미팅 나가기
		const closeSignal = ref(false)  // 미팅 CLOSE

		watch(startSignal, (oldVal) => {
			register()
			axios.post(`meeting/${store.state.meeting.id}/start`, {} ,
				{ headers: { Authorization: `Bearer ${localStorage.getItem("token")}` }}
			).then(res => {
			}).catch(err => {
			})

		})
		watch(leaveSignal, (oldVal) => {
			if (startSignal) {
				leaveRoom()
			}
		})
		watch(endSignal, (oldVal) => {
			if (readySignal) {
				leaveRoom()
			}
		})
		const store = useStore()
		const participants = computed(() => store.state.participants)
		const router = useRouter()
		router.beforeEach((to, from) => {
			if (to.name !== "Meeting") {
				store.dispatch('deleteMeeting')
			}
		})

		const getMeeting = function () {
			axios({
				url: `/meeting/${store.state.meeting.id}`,
				method: "GET",
				headers: { Authorization: `Bearer ${localStorage.getItem("token")}` },
			})
				.then((res) => {
					console.log(res)
					if (res.data.data.status === "RUNNING") {
						startSignal.value = true
					} 
				})
				.catch((err) => {
				});
		}

		return { 
			CloseBold, readySignal, leaveSignal, closeSignal,
			openAside, asideCategory, endSignal, startSignal, participants, categoryKorName,
			wholeVideosWrapper, maxWidth, ratio, setMargin, width, height, windowWidth,
		}
	},
})
</script>

<style scoped>
.meeting-container {
	display: flex;
  flex-flow: column;
  height: 100vh;
	background-color: #F4F4F5;
}

.meeting-content {
	flex: 1 1 auto;
	height: 500px;
	display: flex;
	flex-direction: row;
}

@media screen and (max-width: 600px) {
	.meeting-content {
		flex-direction: column;
		height: 250px;
	}
}

.meeting-footer {
	flex: 0 1 66px;
	border-radius: 10px;
	box-shadow: rgba(0, 0, 0, 0.24) 0px 3px 8px;
	margin: 5px;
}

.meeting-content-main {
	display: flex;
	align-content: center;
	flex-wrap: wrap;
	align-items: center;
	justify-content: center;
	vertical-align: middle;
	flex: 1;
	border-radius: 10px;
	box-shadow: rgba(0, 0, 0, 0.24) 0px 3px 8px;
	margin: 5px;
}

.meeting-content-aside {
	width: 420px;
	display: flex;
	flex-direction: column;
	border-radius: 10px;
	box-shadow: rgba(0, 0, 0, 0.24) 0px 3px 8px;
	margin: 5px;
}

@media screen and (max-width: 600px) {
	.meeting-content-aside {
		height: 50%;
	}
	.meeting-content-main {
		height: 160px;
	}
}

.video-wrapper {
	position: relative;
	vertical-align: middle;
	align-self: center;
	border-radius: 10px;
	overflow: hidden;
	display: inline-block;
	box-shadow: var(--shadow-dark);
	background: #fff;
	animation: show 0.4s ease;
}
</style>