pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    REGISTRY   = 'localhost:5000'
    IMAGE      = "${REGISTRY}/cassier-q-api"
    DEPLOY_DIR = '/opt/apps/cassier-q-api'
  }

  stages {
    stage('Build image') {
      steps {
        script {
          env.IMAGE_TAG = env.TAG_NAME ?: "${env.BRANCH_NAME}-${env.BUILD_NUMBER}".replaceAll('[^A-Za-z0-9_.-]', '-')
        }
        sh 'docker build -t $IMAGE:$IMAGE_TAG .'
      }
    }

    stage('Push to registry') {
      when { buildingTag() }
      steps {
        sh '''
          docker tag  $IMAGE:$IMAGE_TAG $IMAGE:latest
          docker push $IMAGE:$IMAGE_TAG
          docker push $IMAGE:latest
        '''
      }
    }

    stage('Deploy') {
      when { buildingTag() }
      steps {
        sh '''
          if [ ! -f $DEPLOY_DIR/docker-compose.yml ]; then
            echo "Skip deploy: $DEPLOY_DIR/docker-compose.yml belum ada"
            exit 0
          fi
          cd $DEPLOY_DIR
          IMAGE_TAG=$IMAGE_TAG docker compose up -d
          docker compose ps
        '''
      }
    }
  }

  post {
    always {
      sh 'docker image prune -f || true'
    }
  }
}
